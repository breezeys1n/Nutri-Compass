# app.py - 修改文件路径

import os
import json
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import chromadb
from chromadb.config import Settings
from sentence_transformers import SentenceTransformer
import uvicorn

# 获取当前文件所在目录
BASE_DIR = os.path.dirname(os.path.abspath(__file__))

app = FastAPI(title="膳愈 RAG 食谱检索服务")

# 允许跨域请求
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ========== 数据模型 ==========
class RecipeQuery(BaseModel):
    ingredients: List[str] = []
    health_goal: Optional[str] = ""
    cuisine_preference: Optional[str] = ""
    top_k: int = 3
    return_raw: Optional[bool] = False

class SimplifiedRecipe(BaseModel):
    name: str
    cuisine: str
    ingredients: List[Dict[str, str]]
    steps: List[str]

class RecipeResult(BaseModel):
    id: str
    name: str
    cuisine: str
    health_tags: List[str]
    similarity_score: float
    quality_score: Optional[float] = 0.0
    matched_ingredients: List[str] = []
    recipe: Optional[SimplifiedRecipe] = None

class SearchResponse(BaseModel):
    results: List[RecipeResult]
    query_analysis: Dict[str, Any]

# ========== 食材知识库 ==========
class IngredientKB:
    def __init__(self, kb_file: str = None):
        if kb_file is None:
            kb_file = os.path.join(BASE_DIR, 'ingredient_kb.json')
        
        try:
            with open(kb_file, 'r', encoding='utf-8') as f:
                self.kb = json.load(f)
            
            self.canonical_map = self.kb.get('canonical_map', {})
            self.synonym_groups = self.kb.get('synonym_groups', {})
            
            print(f"✅ 食材知识库加载成功")
            print(f"   - 可识别食材: {len(self.canonical_map)} 种")
            print(f"   - 同义词组: {len(self.synonym_groups)} 组")
        except Exception as e:
            print(f"⚠️ 加载知识库失败: {e}")
            self.canonical_map = {}
            self.synonym_groups = {}
    
    def expand(self, ingredient: str) -> List[str]:
        if ingredient in self.canonical_map:
            standard = self.canonical_map[ingredient]
            return self.synonym_groups.get(standard, [ingredient])
        if ingredient in self.synonym_groups:
            return self.synonym_groups[ingredient]
        return [ingredient]
    
    def is_match(self, query_ing: str, recipe_ing: str) -> bool:
        if query_ing == recipe_ing:
            return True
        variants = self.expand(query_ing)
        return recipe_ing in variants

# ========== 向量检索服务 ==========
class RecipeRetrievalService:
    def __init__(self):
        print("正在加载向量模型...")
        self.embed_model = SentenceTransformer('BAAI/bge-small-zh-v1.5')
        
        print("正在加载食材知识库...")
        self.kb = IngredientKB()
        
        print("正在初始化向量数据库...")
        # 向量数据库保存在 rag-service 目录下
        db_path = os.path.join(BASE_DIR, 'recipe_vector_db')
        self.chroma_client = chromadb.PersistentClient(
            path=db_path,
            settings=Settings(anonymized_telemetry=False)
        )
        
        self.collection = self.chroma_client.get_or_create_collection(
            name="nutricompass_recipes",
            metadata={"hnsw:space": "cosine"}
        )
        
        # 加载食谱数据
        self.all_recipes = []
        self.recipe_map = {}
        self.load_all_recipes()
        
        if self.collection.count() == 0:
            print("数据库为空，正在加载初始食谱数据...")
            self.load_initial_recipes()
        else:
            print(f"服务初始化完成，当前有 {self.collection.count()} 条食谱")
    
    def load_all_recipes(self):
        """加载所有原始食谱到内存"""
        try:
            # 从 data/recipes.json 读取
            recipe_path = os.path.join(BASE_DIR, 'data', 'recipes.json')
            if os.path.exists(recipe_path):
                with open(recipe_path, 'r', encoding='utf-8') as f:
                    self.all_recipes = json.load(f)
                
                for recipe in self.all_recipes:
                    url = recipe.get('url', '')
                    recipe_id = url.split('/')[-1].replace('.html', '') if url else ''
                    if recipe_id:
                        self.recipe_map[recipe_id] = recipe
                
                print(f"已从 {recipe_path} 加载 {len(self.all_recipes)} 条原始食谱到内存")
            else:
                print(f"未找到 {recipe_path} 文件")
        except Exception as e:
            print(f"加载原始食谱失败: {e}")
            import traceback
            traceback.print_exc()
    
    def load_initial_recipes(self):
        """加载初始食谱数据集到向量数据库"""
        try:
            recipes = self.all_recipes if self.all_recipes else []
            
            if not recipes:
                print("没有原始食谱数据，使用示例数据...")
                if not os.path.exists(os.path.join(BASE_DIR, 'data')):
                    os.makedirs(os.path.join(BASE_DIR, 'data'))
                self.create_sample_recipes()
                with open(os.path.join(BASE_DIR, 'data', 'recipes.json'), 'r', encoding='utf-8') as f:
                    recipes = json.load(f)
            
            texts = []
            metadatas = []
            ids = []
            
            for i, recipe in enumerate(recipes):
                url = recipe.get('url', '')
                recipe_id = url.split('/')[-1].replace('.html', '') if url else f'rec_{i}'
                
                ingredients_list = [ing['name'] for ing in recipe.get('ingredients', [])]
                ingredients_str = ','.join(ingredients_list)
                
                cuisine = recipe.get('cuisine', '家常菜')
                title = recipe.get('title', '')
                text = f"菜名：{title} | 菜系：{cuisine} | 食材：{ingredients_str}"
                texts.append(text)
                
                quality_score = recipe.get('quality_score', 0)
                metadatas.append({
                    "name": title,
                    "cuisine": cuisine,
                    "ingredients": ingredients_str,
                    "quality_score": str(quality_score),
                })
                
                ids.append(recipe_id)
            
            batch_size = 100
            for i in range(0, len(texts), batch_size):
                end = min(i + batch_size, len(texts))
                self.collection.add(
                    documents=texts[i:end],
                    metadatas=metadatas[i:end],
                    ids=ids[i:end]
                )
                print(f"已添加批次 {i//batch_size + 1}/{(len(texts)-1)//batch_size + 1}")
            
            print(f"成功加载 {len(recipes)} 条食谱到向量数据库")
            
        except Exception as e:
            print(f"加载初始数据失败: {e}")
            import traceback
            traceback.print_exc()
    
    def create_sample_recipes(self):
        """创建示例食谱数据"""
        sample_recipes = [
            {
                "url": "https://example.com/recipe-001",
                "title": "土豆炖牛肉",
                "cuisine": "家常菜",
                "ingredients": [
                    {"name": "土豆", "amount": "2个", "group": "主料"},
                    {"name": "牛肉", "amount": "500g", "group": "主料"},
                    {"name": "姜", "amount": "适量", "group": "辅料"},
                    {"name": "葱", "amount": "适量", "group": "辅料"},
                    {"name": "酱油", "amount": "适量", "group": "调料"}
                ],
                "steps": [
                    "牛肉切块，焯水去血沫",
                    "土豆去皮切块",
                    "锅中热油，炒香姜葱",
                    "放入牛肉翻炒，加酱油",
                    "加水炖1小时，加入土豆再炖20分钟"
                ],
                "quality_score": 0.85
            },
            {
                "url": "https://example.com/recipe-002",
                "title": "酸辣土豆丝",
                "cuisine": "川菜",
                "ingredients": [
                    {"name": "土豆", "amount": "2个", "group": "主料"},
                    {"name": "干辣椒", "amount": "适量", "group": "辅料"},
                    {"name": "醋", "amount": "适量", "group": "调料"},
                    {"name": "盐", "amount": "适量", "group": "调料"},
                    {"name": "蒜", "amount": "适量", "group": "辅料"}
                ],
                "steps": [
                    "土豆切丝，泡水去除淀粉",
                    "热锅凉油，爆香干辣椒和蒜",
                    "放入土豆丝快速翻炒",
                    "加入醋和盐调味",
                    "炒至断生即可出锅"
                ],
                "quality_score": 0.88
            }
        ]
        
        recipe_path = os.path.join(BASE_DIR, 'data', 'recipes.json')
        with open(recipe_path, 'w', encoding='utf-8') as f:
            json.dump(sample_recipes, f, ensure_ascii=False, indent=2)
        
        print(f"已创建示例食谱数据（2条）到 {recipe_path}")
    
    def search(self, query: RecipeQuery) -> List[Dict]:
        """检索食谱"""
        processed_results = []
        
        if not query.ingredients or len(query.ingredients) == 0:
            return processed_results
        
        print(f"\n开始检索，食材: {query.ingredients}")
        
        # 扩展查询食材
        expanded_ingredients = []
        for ing in query.ingredients:
            variants = self.kb.expand(ing)
            expanded_ingredients.extend(variants)
            print(f"  扩展 '{ing}' → {variants}")
        
        # 关键词匹配
        keyword_matches = []
        query_ingredients = [ing.strip() for ing in query.ingredients]
        
        for recipe in self.all_recipes:
            ingredients = recipe.get('ingredients', [])
            ingredient_names = [ing.get('name', '') for ing in ingredients]
            
            match_count = 0
            matched_ingredients = []
            
            for q_ing in query_ingredients:
                for r_ing in ingredient_names:
                    if self.kb.is_match(q_ing, r_ing):
                        match_count += 1
                        matched_ingredients.append(f"{q_ing}→{r_ing}")
                        break
            
            if match_count > 0:
                url = recipe.get('url', '')
                recipe_id = url.split('/')[-1].replace('.html', '') if url else ''
                
                match_score = match_count / len(query.ingredients)
                quality_score = recipe.get('quality_score', 0)
                
                keyword_matches.append({
                    'id': recipe_id,
                    'recipe': recipe,
                    'match_count': match_count,
                    'score': match_score,
                    'quality_score': quality_score,
                    'matched_ingredients': matched_ingredients
                })
        
        keyword_matches.sort(key=lambda x: (x['match_count'], x['quality_score']), reverse=True)
        
        for match in keyword_matches[:query.top_k]:
            recipe_data = match['recipe']
            
            result = {
                "id": match['id'],
                "name": recipe_data.get('title', ''),
                "cuisine": recipe_data.get('cuisine', ''),
                "health_tags": [recipe_data.get('cuisine', '')],
                "similarity_score": match['score'],
                "quality_score": match['quality_score'],
                "matched_ingredients": match['matched_ingredients']
            }
            
            simplified_recipe = {
                "name": recipe_data.get('title', ''),
                "cuisine": recipe_data.get('cuisine', ''),
                "ingredients": recipe_data.get('ingredients', []),
                "steps": recipe_data.get('steps', [])
            }
            result["recipe"] = simplified_recipe
            
            processed_results.append(result)
        
        return processed_results[:query.top_k]

# 初始化服务
retrieval_service = RecipeRetrievalService()

@app.post("/api/search_recipes", response_model=SearchResponse)
async def search_recipes(query: RecipeQuery):
    try:
        results = retrieval_service.search(query)
        
        query_analysis = {
            "ingredients_used": query.ingredients,
            "health_goal": query.health_goal,
            "result_count": len(results),
            "return_raw": query.return_raw
        }
        
        return SearchResponse(
            results=results,
            query_analysis=query_analysis
        )
    
    except Exception as e:
        print(f"检索错误: {e}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/health")
async def health_check():
    return {
        "status": "healthy",
        "recipe_count": retrieval_service.collection.count(),
        "memory_recipes": len(retrieval_service.all_recipes),
        "kb_ingredients": len(retrieval_service.kb.canonical_map)
    }

@app.get("/api/test/{ingredient}")
async def test_ingredient(ingredient: str):
    expanded = retrieval_service.kb.expand(ingredient)
    return {
        "ingredient": ingredient,
        "expanded": expanded
    }

if __name__ == "__main__":
    print("=" * 50)
    print("膳愈 RAG 食谱检索服务启动中...")
    print(f"工作目录: {BASE_DIR}")
    print("已集成食材知识库，支持语义匹配")
    print("=" * 50)
    uvicorn.run(app, host="0.0.0.0", port=8001)