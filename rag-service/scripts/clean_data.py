"""
美食天下菜谱清洗评分系统 - 增强版
增加智能去重和合并功能
同时保存到 cleaned 文件夹和 data 文件夹
"""

import json
import os
import re
from collections import Counter, defaultdict
import pandas as pd
import numpy as np
from typing import Dict, List, Set, Tuple, Any
import hashlib
from datetime import datetime
from difflib import SequenceMatcher

class RecipeCleanerScorer:
    """菜谱清洗评分器 - 增强版"""
    
    def __init__(self, data_dir='./meishichina_data'):
        self.data_dir = data_dir
        self.raw_data = {}  # 原始数据 {菜系: [菜谱列表]}
        self.cleaned_data = {}  # 清洗后数据
        self.scored_data = {}  # 评分后数据
        
        # 评分权重配置
        self.weights = {
            'ingredient_completeness': 0.25,
            'step_detailed': 0.30,
            'title_quality': 0.15,
            'ingredient_standard': 0.15,
            'authenticity': 0.15
        }
        
        # 各菜系的标志性食材
        self.cuisine_signatures = {
            '川菜': ['花椒', '辣椒', '豆瓣酱', '泡椒', '豆豉', '郫县', '麻辣', '红油'],
            '粤菜': ['蚝油', '生抽', '老抽', '姜', '葱', '蒜', '米酒', '陈皮', '叉烧', '煲仔'],
            '鲁菜': ['葱', '姜', '蒜', '酱油', '甜面酱', '八角', '海参', '大虾'],
            '苏菜': ['糖', '料酒', '酱油', '葱', '姜', '八角', '年糕', '熏鱼'],
            '浙菜': ['醋', '糖', '料酒', '酱油', '葱', '姜', '年糕', '龙井'],
            '闽菜': ['糖', '醋', '料酒', '酱油', '红糟', '虾油', '沙茶', '佛跳墙'],
            '湘菜': ['辣椒', '豆豉', '蒜', '姜', '腊肉', '剁椒', '熏'],
            '徽菜': ['火腿', '冬笋', '香菇', '冰糖', '酱油', '臭鳜鱼']
        }
        
        # 同义菜名映射（用于合并相似菜名）
        self.synonym_mapping = {
            '木须肉': ['木须肉', '木须肉片'],
            '煲仔饭': ['腊肠煲仔饭', '腊肠窝蛋煲仔饭', '腊味煲仔饭', '香肠煲仔饭', '广式腊味煲仔饭'],
            '糖醋里脊': ['糖醋里脊'],
            '鱼香肉丝': ['鱼香肉丝'],
            '麻婆豆腐': ['麻婆豆腐'],
            '口水鸡': ['口水鸡'],
            '宫保鸡丁': ['宫保鸡丁'],
            '水煮鱼': ['水煮鱼片', '水煮鱼'],
            '回锅肉': ['回锅肉', '双椒回锅肉'],
        }
        
        # 需要过滤的标题词
        self.bad_title_words = ['菜谱', '做法', '大全', '图解', '步骤', '教程', '窍门', '美食']
        
        # 常见食材别名映射
        self.ingredient_mapping = {
            '鸡脯肉': '鸡胸肉',
            '鸡腿肉': '鸡腿',
            '猪肉末': '猪肉',
            '牛肉末': '牛肉',
            '郫县豆瓣': '郫县豆瓣酱',
            '花生米': '花生',
            '蒜头': '大蒜',
            '生姜': '姜',
            '小葱': '葱',
            '香葱': '葱',
            '青葱': '葱',
            '生抽酱油': '生抽',
            '老抽酱油': '老抽',
            '白砂糖': '白糖',
            '绵白糖': '白糖',
            '细砂糖': '白糖',
            '色拉油': '食用油',
            '菜籽油': '食用油',
            '花生油': '食用油',
            '玉米淀粉': '淀粉',
            '土豆淀粉': '淀粉',
            '红薯淀粉': '淀粉',
        }
    
    def load_all_data(self):
        """加载所有菜系的JSON文件"""
        print("="*60)
        print("📂 加载原始数据...")
        print("="*60)
        
        total = 0
        for filename in os.listdir(self.data_dir):
            if filename.endswith('_recipes.json') and filename != 'summary.json':
                cuisine = filename.replace('_recipes.json', '')
                filepath = os.path.join(self.data_dir, filename)
                
                try:
                    with open(filepath, 'r', encoding='utf-8') as f:
                        data = json.load(f)
                        self.raw_data[cuisine] = data
                        count = len(data)
                        total += count
                        print(f"   {cuisine}: {count} 条")
                except Exception as e:
                    print(f"   ❌ 加载{filename}失败: {e}")
        
        print(f"\n📊 总计加载: {total} 条菜谱\n")
        return self.raw_data
    
    def clean_title(self, title: str) -> str:
        """清洗菜名"""
        if not title:
            return ""
        
        # 去除常见后缀
        title = re.sub(r'的做法$', '', title)
        title = re.sub(r'（.*?）', '', title)
        title = re.sub(r'\(.*?\)', '', title)
        title = re.sub(r'【.*?】', '', title)
        
        # 去除多余空格
        title = title.strip()
        
        return title
    
    def normalize_title(self, title: str) -> str:
        """归一化菜名（用于合并相似菜）"""
        # 去除所有修饰词
        title = re.sub(r'[家国家庭自制私房正宗简易快手懒人版简易超简单].*?[的版]', '', title)
        title = re.sub(r'[粤川鲁苏浙闽湘徽].*?[菜系风味]', '', title)
        title = re.sub(r'[一二三四五六七八九]个?[人份]', '', title)
        
        # 提取核心菜名
        core_dishes = ['木须肉', '鱼香肉丝', '宫保鸡丁', '麻婆豆腐', '回锅肉', 
                      '口水鸡', '水煮鱼', '酸菜鱼', '糖醋里脊', '煲仔饭', 
                      '佛跳墙', '剁椒鱼头', '虎皮凤爪', '油焖大虾', '葱烧海参']
        
        for core in core_dishes:
            if core in title:
                return core
        
        return title
    
    def titles_are_similar(self, title1: str, title2: str, threshold=0.8) -> bool:
        """判断两个菜名是否相似（用于合并）"""
        # 精确匹配
        if title1 == title2:
            return True
        
        # 归一化后比较
        norm1 = self.normalize_title(title1)
        norm2 = self.normalize_title(title2)
        
        if norm1 == norm2:
            return True
        
        # 检查同义映射
        for canonical, variants in self.synonym_mapping.items():
            if title1 in variants and title2 in variants:
                return True
        
        # 使用字符串相似度
        similarity = SequenceMatcher(None, title1, title2).ratio()
        return similarity > threshold
    
    def clean_ingredients(self, ingredients: List[Dict], cuisine: str) -> List[Dict]:
        """清洗食材列表"""
        cleaned = []
        
        for ing in ingredients:
            name = ing.get('name', '').strip()
            amount = ing.get('amount', '').strip()
            group = ing.get('group', '主料')
            
            if not name or len(name) < 1:
                continue
            
            # 标准化食材名称
            if name in self.ingredient_mapping:
                name = self.ingredient_mapping[name]
            
            if len(name) > 20:
                continue
            
            amount = re.sub(r'\s+', '', amount)
            
            cleaned.append({
                'name': name,
                'amount': amount if amount else '适量',
                'group': group
            })
        
        # 去重
        unique = {}
        for ing in cleaned:
            name = ing['name']
            if name not in unique:
                unique[name] = ing
            else:
                if ing['amount'] != '适量' and unique[name]['amount'] == '适量':
                    unique[name] = ing
        
        return list(unique.values())
    
    def clean_steps(self, steps: List[str]) -> List[str]:
        """清洗步骤"""
        cleaned = []
        
        for step in steps:
            if not step:
                continue
            
            step = re.sub(r'^\d+[\.、]', '', step)
            step = step.strip()
            
            if len(step) < 5:
                continue
            
            if step in ['成品', '成品图', '完成', '出锅']:
                continue
            
            cleaned.append(step)
        
        return cleaned
    
    def score_title_quality(self, title: str) -> float:
        """评分：标题质量"""
        score = 0.5
        
        if 4 <= len(title) <= 15:
            score += 0.3
        elif len(title) < 3:
            score -= 0.2
        elif len(title) > 20:
            score -= 0.1
        
        if any(bad in title for bad in self.bad_title_words):
            score -= 0.2
        
        return max(0, min(1, score))
    
    def score_ingredient_completeness(self, ingredients: List[Dict]) -> float:
        """评分：食材完整度"""
        if not ingredients:
            return 0
        
        score = 0
        ing_count = len(ingredients)
        
        if ing_count >= 8:
            score += 0.4
        elif ing_count >= 5:
            score += 0.3
        elif ing_count >= 3:
            score += 0.2
        
        has_amount = sum(1 for i in ingredients if i.get('amount') and i['amount'] != '适量')
        amount_ratio = has_amount / ing_count if ing_count > 0 else 0
        score += amount_ratio * 0.3
        
        has_group = sum(1 for i in ingredients if i.get('group') and i['group'] not in ['主料', '辅料', '调料'])
        if has_group > 0:
            score += 0.3
        
        return max(0, min(1, score))
    
    def score_step_detailed(self, steps: List[str]) -> float:
        """评分：步骤详细度"""
        if not steps:
            return 0
        
        score = 0
        step_count = len(steps)
        
        if step_count >= 8:
            score += 0.3
        elif step_count >= 5:
            score += 0.2
        elif step_count >= 3:
            score += 0.1
        
        avg_length = np.mean([len(s) for s in steps]) if steps else 0
        if avg_length > 50:
            score += 0.25
        elif avg_length > 30:
            score += 0.2
        elif avg_length > 15:
            score += 0.15
        
        key_verbs = ['切', '炒', '煮', '炖', '炸', '蒸', '腌', '焯', '煸', '爆', '煎', '焖']
        verb_count = 0
        for step in steps:
            for verb in key_verbs:
                if verb in step:
                    verb_count += 1
                    break
        
        verb_density = verb_count / step_count if step_count > 0 else 0
        if verb_density > 0.7:
            score += 0.25
        elif verb_density > 0.5:
            score += 0.15
        elif verb_density > 0.3:
            score += 0.1
        
        control_words = ['分钟', '小时', '火', '温', '熟', '变色', '沸腾', '小火', '中火', '大火']
        has_control = any(any(word in step for word in control_words) for step in steps)
        if has_control:
            score += 0.2
        
        return max(0, min(1, score))
    
    def score_ingredient_standard(self, ingredients: List[Dict]) -> float:
        """评分：食材标准化程度"""
        if not ingredients:
            return 0
        
        score = 0.5
        
        weird_pattern = r'[0-9]|[a-zA-Z]|[（）()]|[:：]'
        weird_count = 0
        for ing in ingredients:
            name = ing.get('name', '')
            if re.search(weird_pattern, name):
                weird_count += 1
        
        if weird_count == 0:
            score += 0.3
        elif weird_count / len(ingredients) < 0.2:
            score += 0.1
        else:
            score -= 0.2
        
        amount_count = sum(1 for i in ingredients if i.get('amount') and i['amount'] != '适量')
        if amount_count / len(ingredients) > 0.5:
            score += 0.2
        
        return max(0, min(1, score))
    
    def score_authenticity(self, recipe: Dict, cuisine: str) -> float:
        """评分：菜系地道性"""
        ingredients = recipe.get('ingredients', [])
        title = recipe.get('title', '')
        
        if cuisine not in self.cuisine_signatures:
            return 0.5
        
        signatures = self.cuisine_signatures[cuisine]
        found = 0
        
        for ing in ingredients:
            name = ing.get('name', '')
            for sig in signatures:
                if sig in name:
                    found += 1
                    break
        
        for sig in signatures:
            if sig in title:
                found += 1
        
        if found >= 4:
            score = 1.0
        elif found == 3:
            score = 0.8
        elif found == 2:
            score = 0.6
        elif found == 1:
            score = 0.4
        else:
            score = 0.3
        
        return score
    
    def comprehensive_score(self, recipe: Dict, cuisine: str) -> Dict:
        """综合评分"""
        title = recipe.get('title', '')
        ingredients = recipe.get('ingredients', [])
        steps = recipe.get('steps', [])
        
        scores = {
            'title_quality': self.score_title_quality(title),
            'ingredient_completeness': self.score_ingredient_completeness(ingredients),
            'step_detailed': self.score_step_detailed(steps),
            'ingredient_standard': self.score_ingredient_standard(ingredients),
            'authenticity': self.score_authenticity(recipe, cuisine)
        }
        
        total = sum(scores[k] * self.weights[k] for k in scores)
        
        return {
            'total': total,
            'details': scores
        }
    
    def group_similar_titles(self, recipes: List[Dict]) -> Dict[str, List[Dict]]:
        """将相似的菜名分组（智能去重）"""
        groups = defaultdict(list)
        used = set()
        
        for i, recipe in enumerate(recipes):
            if i in used:
                continue
            
            title = recipe.get('title', '')
            group = [recipe]
            used.add(i)
            
            for j, other in enumerate(recipes[i+1:], i+1):
                if j in used:
                    continue
                
                other_title = other.get('title', '')
                if self.titles_are_similar(title, other_title):
                    group.append(other)
                    used.add(j)
            
            # 用最标准的菜名作为组名
            canonical_name = self.normalize_title(title)
            groups[canonical_name].extend(group)
        
        return groups
    
    def select_best_recipes(self, recipes: List[Dict], cuisine: str, top_k=2) -> List[Dict]:
        """从相似菜谱中选出最好的几个"""
        # 先对相似菜名分组
        title_groups = self.group_similar_titles(recipes)
        
        selected = []
        
        for canonical_name, group in title_groups.items():
            if len(group) == 1:
                recipe = group[0]
                score_result = self.comprehensive_score(recipe, cuisine)
                recipe['quality_score'] = score_result['total']
                recipe['score_details'] = score_result['details']
                recipe['canonical_name'] = canonical_name
                selected.append(recipe)
            else:
                # 多个版本，评分后选top_k
                scored = []
                for recipe in group:
                    score_result = self.comprehensive_score(recipe, cuisine)
                    scored.append({
                        'score': score_result['total'],
                        'recipe': recipe,
                        'details': score_result['details']
                    })
                
                scored.sort(key=lambda x: x['score'], reverse=True)
                
                for i in range(min(top_k, len(scored))):
                    item = scored[i]
                    recipe = item['recipe']
                    recipe['quality_score'] = item['score']
                    recipe['score_details'] = item['details']
                    recipe['version_rank'] = i + 1
                    recipe['total_versions'] = len(group)
                    recipe['canonical_name'] = canonical_name
                    selected.append(recipe)
        
        return selected
    
    def process_cuisine(self, cuisine: str, recipes: List[Dict]) -> List[Dict]:
        """处理单个菜系的数据"""
        print(f"\n{'='*50}")
        print(f"🍳 处理【{cuisine}】")
        print(f"{'='*50}")
        
        processed = []
        stats = {
            'total': len(recipes),
            'removed_no_ingredients': 0,
            'removed_few_steps': 0,
            'removed_bad_title': 0,
            'kept': 0
        }
        
        for recipe in recipes:
            title = self.clean_title(recipe.get('title', ''))
            ingredients = self.clean_ingredients(recipe.get('ingredients', []), cuisine)
            steps = self.clean_steps(recipe.get('steps', []))
            
            if len(ingredients) < 3:
                stats['removed_no_ingredients'] += 1
                continue
            
            if len(steps) < 2:
                stats['removed_few_steps'] += 1
                continue
            
            if len(title) < 2 or any(bad in title for bad in self.bad_title_words):
                stats['removed_bad_title'] += 1
                continue
            
            cleaned = {
                'url': recipe.get('url', ''),
                'title': title,
                'cuisine': cuisine,
                'ingredients': ingredients,
                'steps': steps,
                'ingredients_count': len(ingredients),
                'steps_count': len(steps),
                'original_title': recipe.get('title', ''),
                'clean_time': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            }
            
            processed.append(cleaned)
            stats['kept'] += 1
        
        print(f"\n  原始: {stats['total']} 条")
        print(f"  清洗后: {stats['kept']} 条")
        print(f"  过滤原因:")
        print(f"    - 食材太少 (<3): {stats['removed_no_ingredients']}")
        print(f"    - 步骤太少 (<2): {stats['removed_few_steps']}")
        print(f"    - 标题无效: {stats['removed_bad_title']}")
        
        best_recipes = self.select_best_recipes(processed, cuisine, top_k=2)
        print(f"  选出最佳版本: {len(best_recipes)} 条 (每个菜名保留1-2个版本)")
        
        return best_recipes
    
    def run(self):
        """运行完整清洗流程"""
        print("\n" + "="*60)
        print("🧹 开始清洗评分流程（增强版）")
        print("="*60)
        
        self.load_all_data()
        
        all_processed = []
        cuisine_stats = {}
        
        for cuisine, recipes in self.raw_data.items():
            best = self.process_cuisine(cuisine, recipes)
            self.scored_data[cuisine] = best
            all_processed.extend(best)
            cuisine_stats[cuisine] = len(best)
        
        self.save_results()
        self.generate_report(cuisine_stats, len(all_processed))
        
        return self.scored_data
    
    def save_results(self):
        """保存清洗评分后的结果 - 同时保存到 cleaned 和 data 文件夹"""
        # 创建 cleaned 输出目录
        output_dir = os.path.join(self.data_dir, 'cleaned')
        os.makedirs(output_dir, exist_ok=True)
        
        # 获取 rag-service 根目录（用于保存到 data 文件夹）
        script_dir = os.path.dirname(os.path.abspath(__file__))
        rag_root = os.path.dirname(script_dir)  # scripts 的上一级是 rag-service
        data_dir = os.path.join(rag_root, 'data')
        os.makedirs(data_dir, exist_ok=True)
        
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        
        all_recipes = []
        for cuisine, recipes in self.scored_data.items():
            if recipes:
                # 保存单个菜系到 cleaned
                filename = os.path.join(output_dir, f'{cuisine}_scored.json')
                with open(filename, 'w', encoding='utf-8') as f:
                    json.dump(recipes, f, ensure_ascii=False, indent=2)
                print(f"\n💾 已保存 {cuisine}: {len(recipes)} 条到 {filename}")
                
                all_recipes.extend(recipes)
        
        # 保存合并的总文件到 cleaned（带时间戳）
        total_file = os.path.join(output_dir, f'all_recipes_scored_{timestamp}.json')
        with open(total_file, 'w', encoding='utf-8') as f:
            json.dump(all_recipes, f, ensure_ascii=False, indent=2)
        print(f"\n📦 已保存总训练集: {len(all_recipes)} 条到 {total_file}")
        
        # ===== 保存两份 recipes.json =====
        
        # 1. 保存到 cleaned 文件夹（覆盖最新的）
        cleaned_recipes_path = os.path.join(output_dir, 'recipes.json')
        with open(cleaned_recipes_path, 'w', encoding='utf-8') as f:
            json.dump(all_recipes, f, ensure_ascii=False, indent=2)
        print(f"📋 已保存到 cleaned/recipes.json: {cleaned_recipes_path}")
        
        # 2. 保存到 data 文件夹（供 RAG 服务使用）
        data_recipes_path = os.path.join(data_dir, 'recipes.json')
        with open(data_recipes_path, 'w', encoding='utf-8') as f:
            json.dump(all_recipes, f, ensure_ascii=False, indent=2)
        print(f"📋 已同步到 data/recipes.json: {data_recipes_path}")
        # =================================
        
        # 保存为CSV方便查看
        df = pd.DataFrame(all_recipes)
        csv_file = os.path.join(output_dir, f'all_recipes_scored_{timestamp}.csv')
        df.to_csv(csv_file, index=False, encoding='utf-8-sig')
        print(f"📊 已导出CSV: {csv_file}")
        
        # 保存评分统计
        score_stats = []
        for recipe in all_recipes:
            if 'quality_score' in recipe:
                score_stats.append({
                    'title': recipe['title'],
                    'canonical_name': recipe.get('canonical_name', recipe['title']),
                    'cuisine': recipe['cuisine'],
                    'score': recipe['quality_score'],
                    'version_rank': recipe.get('version_rank', 1),
                    'total_versions': recipe.get('total_versions', 1),
                    'ingredients_count': recipe['ingredients_count'],
                    'steps_count': recipe['steps_count']
                })
        
        stats_df = pd.DataFrame(score_stats)
        stats_file = os.path.join(output_dir, f'score_stats_{timestamp}.csv')
        stats_df.to_csv(stats_file, index=False, encoding='utf-8-sig')
        print(f"📈 已保存评分统计: {stats_file}")
    
    def generate_report(self, cuisine_stats: Dict, total: int):
        """生成清洗报告"""
        print("\n" + "="*60)
        print("📊 清洗评分报告")
        print("="*60)
        
        total_raw = sum(len(v) for v in self.raw_data.values())
        
        for cuisine, count in cuisine_stats.items():
            raw = len(self.raw_data.get(cuisine, []))
            rate = count / raw * 100 if raw > 0 else 0
            print(f"{cuisine}: {raw} → {count} ({rate:.1f}%)")
        
        print("-"*60)
        print(f"总计: {total_raw} → {total} ({(total/total_raw*100):.1f}%)")
        
        print("\n📈 评分分布建议:")
        print("  - 0.8以上: ⭐⭐⭐⭐⭐ 高质量 (可直接用于训练)")
        print("  - 0.6-0.8: ⭐⭐⭐⭐ 良好 (可作为补充)")
        print("  - 0.4-0.6: ⭐⭐⭐ 一般 (需人工复核)")
        print("  - 0.4以下: ⭐⭐ 低质量 (建议丢弃)")


def main():
    """主函数"""
    # 获取脚本所在目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    rag_root = os.path.dirname(script_dir)
    data_dir = os.path.join(rag_root, 'meishichina_data')
    
    print(f"📁 数据目录: {data_dir}")
    
    cleaner = RecipeCleanerScorer(data_dir=data_dir)
    cleaned_data = cleaner.run()
    
    print("\n✨ 清洗评分完成！")
    print("   数据已保存到:")
    print("   - meishichina_data/cleaned/recipes.json")
    print("   - data/recipes.json（RAG服务使用）")
    print("   建议：优先使用评分0.6以上的数据")


if __name__ == '__main__':
    main()