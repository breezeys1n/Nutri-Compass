import json
import os
import re
from collections import defaultdict
import numpy as np
from typing import Dict, List

class RecipeCleanerScorer:
    """美食天下菜谱清洗评分系统 - RAG 专用平衡版"""
    
    def __init__(self):
        # 路径自动适配逻辑：定位到项目根目录下的 meishichina_data
        script_dir = os.path.dirname(os.path.abspath(__file__))
        self.root_dir = os.path.dirname(script_dir)
        self.data_dir = os.path.join(self.root_dir, 'meishichina_data')
        # 结果保存路径
        self.output_dir = os.path.join(self.data_dir, 'cleaned')
        os.makedirs(self.output_dir, exist_ok=True)

        self.raw_data = {}
        self.cuisines = ['川菜', '粤菜', '鲁菜', '苏菜', '浙菜', '闽菜', '湘菜', '徽菜']
        # 垃圾清洗正则
        self.junk_patterns = re.compile(r"成品图|展示图|步骤图|小贴士|窍门|技巧|温馨提示|享用|开吃|关注|如图|最后一步|装盘")

    def load_all_data(self):
        """加载原始JSON文件"""
        print("="*60)
        print(f"📂 正在从 {self.data_dir} 加载原始数据...")
        total_loaded = 0
        for cuisine in self.cuisines:
            filename = f"{cuisine}_recipes.json"
            filepath = os.path.join(self.data_dir, filename)
            if os.path.exists(filepath):
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    self.raw_data[cuisine] = data
                    total_loaded += len(data)
                    print(f"   已加载 {cuisine}: {len(data)} 条")
        print(f"📊 共计加载: {total_loaded} 条原始数据")

    # --- 评分算法 (兼顾区分度与RAG需求) ---

    def score_title(self, title: str) -> float:
        """标题分：黄金长度 5-12 字"""
        ln = len(title)
        if 5 <= ln <= 12: return 1.0
        if 3 <= ln < 5: return 0.7
        return 0.4

    def score_ingredients(self, ings: List[Dict]) -> float:
        """食材分：采用对数曲线，食材越多且标注越清得分越高"""
        count = len(ings)
        if count == 0: return 0
        # 对数模型：4个食材约0.72分，8个约0.88分，只有极丰富且标注清晰的才拿0.95+
        score = 0.45 + (np.log1p(count) / 3.0)
        return min(1.0, score)

    def score_steps(self, steps: List[str]) -> float:
        """步骤分：步数基础分 + 字数细节分"""
        if not steps or len(steps) < 3: return 0
        
        # 步数贡献 (基础 0.3, 每多一步加 0.1, 8步封顶 0.8)
        step_base = min(0.8, len(steps) * 0.1)
        
        # 细节深度 (平均每步字数超过20字认为非常详尽)
        avg_len = np.mean([len(s) for s in steps])
        detail_bonus = min(0.2, (avg_len / 35) * 0.2)
        
        return min(1.0, step_base + detail_bonus)

    def get_fingerprint(self, r: Dict) -> str:
        """生成去重指纹：核心标题 + 前3种食材"""
        title = re.sub(r"正宗|家常|做法|秘制|（.*?）|\(.*?\)", "", r['title']).strip()
        ings = sorted([i['name'] for i in r.get('ingredients', [])[:3]])
        return f"{title}_{'|'.join(ings)}"

    def process(self):
        self.load_all_data()
        if not self.raw_data:
            print("❌ 错误：未找到数据文件，请检查 meishichina_data 文件夹。")
            return

        final_recipes = []
        print("\n🚀 正在进行深度清洗与平衡评分...")

        for cuisine, recipes in self.raw_data.items():
            groups = defaultdict(list)
            
            for r in recipes:
                # 1. 清洗步骤文字 (保留原始 steps 字段结构)
                raw_steps = r.get('steps', [])
                # 去除步骤前的数字编号
                cleaned_steps = [re.sub(r'^\d+[\.、\s]*', '', s).strip() for s in raw_steps]
                # 过滤垃圾话和太短的步骤
                r['steps'] = [s for s in cleaned_steps if not self.junk_patterns.search(s) and len(s) > 3]
                
                # 过滤掉清洗后步骤过少的无效数据
                if len(r['steps']) < 3: continue

                # 2. 计算平衡评分
                s1 = self.score_title(r['title'])
                s2 = self.score_steps(r['steps'])
                s3 = self.score_ingredients(r.get('ingredients', []))
                
                # 最终权重分配：步骤(50%) + 食材(35%) + 标题(15%)
                r['quality_score'] = round(s1 * 0.15 + s2 * 0.5 + s3 * 0.35, 2)
                
                # 3. 按指纹分组准备去重
                fp = self.get_fingerprint(r)
                groups[fp].append(r)

            cuisine_kept = 0
            for fp, members in groups.items():
                # 同名同主料菜谱，保留分值最高的 2 个版本（兼顾多样性与质量）
                top_members = sorted(members, key=lambda x: x['quality_score'], reverse=True)[:2]
                for item in top_members:
                    item['cuisine'] = cuisine # 确保 cuisine 字段存在
                    final_recipes.append(item)
                    cuisine_kept += 1
            print(f"   ✅ {cuisine}: 清洗完成，保留 {cuisine_kept} 条")

        self.save_all(final_recipes)

    def save_all(self, data: List[Dict]):
        """严格按照原始 RAG 要求的格式保存 JSON"""
        # 1. 保存到 meishichina_data/cleaned/recipes.json (备份)
        path1 = os.path.join(self.output_dir, 'recipes.json')
        with open(path1, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

        # 2. 保存到 rag-service/data/recipes.json (RAG 核心使用)
        rag_data_dir = os.path.join(self.root_dir, 'data')
        os.makedirs(rag_data_dir, exist_ok=True)
        path2 = os.path.join(rag_data_dir, 'recipes.json')
        with open(path2, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

        print("\n" + "="*60)
        print(f"✨ 处理成功！最终 RAG 库规模: {len(data)} 条")
        print(f"📈 预估平均分: {sum(r['quality_score'] for r in data)/len(data) if data else 0:.2f}")
        print(f"📂 RAG 路径: {path2}")
        print("="*60)

if __name__ == "__main__":
    cleaner = RecipeCleanerScorer()
    cleaner.process()