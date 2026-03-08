# ingredient_learner.py - 混合策略版
"""
食材自学习模块 - 混合策略
基础规则覆盖常见食材 + 从数据中学习补充
只修改了文件路径，逻辑完全不变
"""

import json
import os
import re
from collections import defaultdict, Counter
from typing import Dict, List, Set, Tuple

class IngredientLearner:
    def __init__(self, recipes_file: str = None):
        # 修改：如果没指定路径，使用相对于脚本的位置
        if recipes_file is None:
            # 获取当前脚本所在目录
            base_dir = os.path.dirname(os.path.abspath(__file__))
            # 指向 data/recipes.json
            self.recipes_file = os.path.join(base_dir, '..', 'data', 'recipes.json')
        else:
            self.recipes_file = recipes_file
            
        self.learned_relations = defaultdict(set)  # 从数据学到的关系
        self.all_ingredients = set()
        
        # ===== 1. 基础规则（手工维护，覆盖90%常见食材）=====
        self.base_rules = {
            # 猪肉类
            '猪肉': ['猪肉', '五花肉', '里脊肉', '瘦肉', '猪里脊', '猪瘦肉', '前腿肉', '后腿肉'],
            '五花肉': ['五花肉', '猪五花', '三层肉', '带皮五花肉'],
            '排骨': ['排骨', '猪排骨', '肋排', '小排', '仔排'],
            
            # 牛肉类
            '牛肉': ['牛肉', '牛腩', '牛里脊', '牛腱子', '肥牛'],
            
            # 羊肉类
            '羊肉': ['羊肉', '羊肉卷'],
            
            # 鸡肉类
            '鸡肉': ['鸡肉', '鸡腿', '鸡胸肉', '鸡翅', '鸡爪'],
            
            # 蛋类
            '鸡蛋': ['鸡蛋', '鸭蛋', '鹌鹑蛋'],
            
            # ===== 蔬菜类（基础覆盖，不怕数据少）=====
            # 叶菜类
            '白菜': ['白菜', '大白菜', '小白菜', '娃娃菜'],
            '青菜': ['青菜', '油菜', '上海青', '小油菜', '菜心'],
            '菠菜': ['菠菜'],
            '生菜': ['生菜'],
            '油麦菜': ['油麦菜'],
            '空心菜': ['空心菜'],
            '韭菜': ['韭菜'],
            '芹菜': ['芹菜', '西芹'],
            '包菜': ['包菜', '圆白菜', '卷心菜'],
            
            # 根茎类
            '土豆': ['土豆', '马铃薯', '洋芋'],
            '萝卜': ['萝卜', '白萝卜', '胡萝卜', '青萝卜'],
            '胡萝卜': ['胡萝卜', '红萝卜'],
            '红薯': ['红薯', '地瓜'],
            '山药': ['山药'],
            '芋头': ['芋头'],
            '莲藕': ['莲藕', '藕'],
            '洋葱': ['洋葱', '圆葱'],
            
            # 瓜果类
            '黄瓜': ['黄瓜'],
            '冬瓜': ['冬瓜'],
            '南瓜': ['南瓜'],
            '苦瓜': ['苦瓜'],
            '丝瓜': ['丝瓜'],
            '西红柿': ['西红柿', '番茄'],
            '茄子': ['茄子'],
            '辣椒': ['辣椒', '青椒', '红椒'],
            '尖椒': ['尖椒'],
            
            # 豆类
            '豆角': ['豆角', '四季豆'],
            '豌豆': ['豌豆'],
            '毛豆': ['毛豆'],
            
            # 豆制品
            '豆腐': ['豆腐', '嫩豆腐', '老豆腐'],
            '豆干': ['豆干'],
            '腐竹': ['腐竹'],
            '豆皮': ['豆皮'],
            
            # 菌菇类
            '香菇': ['香菇'],
            '木耳': ['木耳'],
            '金针菇': ['金针菇'],
            
            # 调味类
            '葱': ['葱', '小葱', '香葱'],
            '姜': ['姜', '生姜'],
            '蒜': ['蒜', '大蒜'],
            '蒜苗': ['蒜苗'],
        }
        
        # 2. 从数据中学习补充（只学高频的）
        self._learn_from_data(self.recipes_file)
    
    def _learn_from_data(self, recipes_file: str):
        """从数据中学习（严格控制）"""
        try:
            with open(recipes_file, 'r', encoding='utf-8') as f:
                recipes = json.load(f)
            
            print(f"\n📊 开始从 {len(recipes)} 条菜谱中学习...")
            
            # 统计食材出现频率
            freq = Counter()
            for recipe in recipes:
                for ing in recipe.get('ingredients', []):
                    name = ing.get('name', '')
                    if name:
                        freq[name] += 1
            
            # 找出高频食材（出现>=3次）
            common_ingredients = {name for name, count in freq.items() if count >= 3}
            print(f"   高频食材: {len(common_ingredients)} 种")
            
            # 学习同现关系
            co_occur = defaultdict(Counter)
            for recipe in recipes:
                ings = [ing['name'] for ing in recipe.get('ingredients', [])]
                ings = [i for i in ings if i in common_ingredients]
                
                for i in range(len(ings)):
                    for j in range(i+1, len(ings)):
                        co_occur[ings[i]][ings[j]] += 1
                        co_occur[ings[j]][ings[i]] += 1
            
            # 找出强关联（同现次数多，且名字相似）
            for ing1, related in co_occur.items():
                for ing2, count in related.items():
                    if count >= 3:  # 至少同现3次
                        # 检查是否名字相似（包含相同关键词）
                        if self._names_are_similar(ing1, ing2):
                            print(f"   发现关联: {ing1} ↔ {ing2} (同现{count}次)")
                            self.learned_relations[ing1].add(ing2)
                            self.learned_relations[ing2].add(ing1)
            
        except Exception as e:
            print(f"⚠️ 学习失败: {e}")
    
    def _names_are_similar(self, name1: str, name2: str) -> bool:
        """判断两个食材名字是否相似"""
        # 如果完全相同
        if name1 == name2:
            return True
        
        # 包含关系（鸡腿和鸡胸）
        if name1 in name2 or name2 in name1:
            return True
        
        # 同义词对
        synonym_pairs = [
            ('土豆', '马铃薯'), ('西红柿', '番茄'),
            ('辣椒', '青椒'), ('猪肉', '五花肉'),
        ]
        
        for a, b in synonym_pairs:
            if (name1 == a and name2 == b) or (name1 == b and name2 == a):
                return True
        
        return False
    
    def build_knowledge_base(self) -> Dict:
        """构建最终的知识库"""
        kb = {
            'synonym_groups': {},
            'canonical_map': {}
        }
        
        # 1. 先加入基础规则
        for canonical, variants in self.base_rules.items():
            kb['synonym_groups'][canonical] = variants
            for v in variants:
                kb['canonical_map'][v] = canonical
        
        # 2. 加入学习到的关系（补充）
        learned_added = 0
        for ing, related in self.learned_relations.items():
            # 如果这个食材已经有标准名
            if ing in kb['canonical_map']:
                canonical = kb['canonical_map'][ing]
                for rel in related:
                    if rel not in kb['synonym_groups'][canonical]:
                        kb['synonym_groups'][canonical].append(rel)
                        kb['canonical_map'][rel] = canonical
                        learned_added += 1
        
        print(f"\n✅ 学习成果:")
        print(f"   - 基础规则: {len(self.base_rules)} 组")
        print(f"   - 学习补充: {learned_added} 条关系")
        
        return kb
    
    def print_stats(self, kb: Dict):
        """打印统计"""
        print("\n" + "="*60)
        print("📊 知识库统计")
        print("="*60)
        
        categories = {
            '猪肉类': ['猪肉', '五花肉', '排骨'],
            '牛羊肉': ['牛肉', '羊肉'],
            '鸡肉类': ['鸡肉'],
            '蛋类': ['鸡蛋'],
            '叶菜类': ['白菜', '青菜', '菠菜', '生菜', '油麦菜', '空心菜', '韭菜', '芹菜', '包菜'],
            '根茎类': ['土豆', '萝卜', '胡萝卜', '红薯', '山药', '芋头', '莲藕', '洋葱'],
            '瓜果类': ['黄瓜', '冬瓜', '南瓜', '苦瓜', '丝瓜', '西红柿', '茄子', '辣椒', '尖椒'],
            '豆类': ['豆角', '豌豆', '毛豆'],
            '豆制品': ['豆腐', '豆干', '腐竹', '豆皮'],
            '菌菇类': ['香菇', '木耳', '金针菇'],
            '调味类': ['葱', '姜', '蒜', '蒜苗'],
        }
        
        for category, items in categories.items():
            count = 0
            for item in items:
                if item in kb['synonym_groups']:
                    count += len(kb['synonym_groups'][item])
            print(f"   {category}: {count} 种")
        
        print(f"\n总计: {len(kb['canonical_map'])} 种可识别食材")

def main():
    """主函数"""
    print("🔧 食材知识库学习器启动...")
    
    # 修改：自动定位到正确的文件路径
    base_dir = os.path.dirname(os.path.abspath(__file__))
    recipes_file = os.path.join(base_dir, '..', 'data', 'recipes.json')
    
    print(f"食谱文件: {recipes_file}")
    
    learner = IngredientLearner(recipes_file)
    kb = learner.build_knowledge_base()
    learner.print_stats(kb)
    
    # 测试
    print("\n" + "="*60)
    print("🧪 测试扩展结果")
    print("="*60)
    
    test_cases = [
        '五花肉', '猪肉', '鸡腿', '土豆', '西红柿', '白菜', '辣椒', '豆腐'
    ]
    
    for ing in test_cases:
        # 找到标准名
        canonical = kb['canonical_map'].get(ing, ing)
        variants = kb['synonym_groups'].get(canonical, [ing])
        print(f"\n{ing} → {variants}")
        
        # 测试相似判断
        if ing == '五花肉':
            print(f"  五花肉 和 猪肉 是否相同? {'猪肉' in variants}")
        if ing == '鸡腿':
            print(f"  鸡腿 和 鸡肉 是否相同? {'鸡肉' in variants}")
        if ing == '土豆':
            print(f"  马铃薯 和 土豆 是否相同? {'马铃薯' in variants}")
    
    # 修改：保存到 rag-service 根目录
    output_file = os.path.join(base_dir, '..', 'ingredient_kb.json')
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(kb, f, ensure_ascii=False, indent=2)
    print(f"\n💾 知识库已保存到 {output_file}")

if __name__ == '__main__':
    main()