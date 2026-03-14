"""
美食天下八大菜系爬虫 - 7-8页版
自动爬取每个菜系7-8页的菜谱数据
"""

import requests
from bs4 import BeautifulSoup
import json
import time
import random
import os
from urllib.parse import urljoin
from datetime import datetime
import re

class MeiShiChinaCrawler:
    """美食天下爬虫 - 7页版"""
    
    def __init__(self, save_dir=None):
        # 如果没指定 save_dir，使用相对于脚本的位置
        if save_dir is None:
            # 获取 rag-service 目录
            base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
            self.save_dir = os.path.join(base_dir, 'meishichina_data')
        else:
            self.save_dir = save_dir
        
        self.base_url = 'https://home.meishichina.com'
        
        # 八大菜系
        self.cuisines = {
            '川菜': '/recipe/chuancai/page/{}/',
            '粤菜': '/recipe/yuecai/page/{}/',
            '鲁菜': '/recipe/lucai/page/{}/',
            '苏菜': '/recipe/sucai/page/{}/',
            '浙菜': '/recipe/zhecai/page/{}/',
            '闽菜': '/recipe/mincai/page/{}/',
            '湘菜': '/recipe/xiangcai/page/{}/',
            '徽菜': '/recipe/huicai/page/{}/'
        }
        
        # 创建保存目录
        os.makedirs(self.save_dir, exist_ok=True)
        
        # 请求头
        self.headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
            'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
            'Connection': 'keep-alive',
            'Upgrade-Insecure-Requests': '1',
        }
        
        self.session = requests.Session()
        self.stats = {
            'total_requests': 0,
            'success': 0,
            'failed': 0,
            'total_recipes': 0
        }
    
    def safe_request(self, url, max_retries=3):
        """安全的网络请求"""
        self.stats['total_requests'] += 1
        
        for i in range(max_retries):
            try:
                print(f"     请求: {url}")
                response = self.session.get(
                    url, 
                    headers=self.headers, 
                    timeout=15,
                    allow_redirects=True
                )
                
                if response.status_code == 200:
                    self.stats['success'] += 1
                    return response
                else:
                    print(f"     状态码: {response.status_code}，重试 {i+1}/{max_retries}")
                    
            except Exception as e:
                print(f"     请求异常: {e}，重试 {i+1}/{max_retries}")
            
            time.sleep(random.uniform(3, 5))
        
        self.stats['failed'] += 1
        print(f"     请求失败: {url}")
        return None
    
    def get_detail_urls_from_page(self, html, cuisine):
        """从列表页提取所有详情页URL"""
        soup = BeautifulSoup(html, 'html.parser')
        detail_urls = set()
        
        # 查找所有包含 recipe- 的链接
        all_links = soup.find_all('a', href=True)
        
        for link in all_links:
            href = link['href']
            text = link.text.strip()
            
            # 菜谱详情页的特征
            if '/recipe-' in href and '.html' in href:
                # 构造完整URL
                if href.startswith('http'):
                    full_url = href
                else:
                    full_url = urljoin(self.base_url, href)
                
                # 过滤掉非菜谱链接
                if len(text) > 2 and len(text) < 50:
                    if not any(keyword in text for keyword in ['首页', '下一页', '上一页', '返回', '登录', '注册', '客户端']):
                        detail_urls.add(full_url)
        
        # 也从图片alt中提取
        imgs = soup.find_all('img', alt=True)
        for img in imgs:
            alt = img['alt']
            if len(alt) > 2 and alt != '美食天下客户端':
                parent_link = img.find_parent('a')
                if parent_link and parent_link.get('href'):
                    href = parent_link['href']
                    if '/recipe-' in href:
                        if href.startswith('http'):
                            full_url = href
                        else:
                            full_url = urljoin(self.base_url, href)
                        detail_urls.add(full_url)
        
        return list(detail_urls)
    
    def parse_recipe_detail(self, detail_url):
        """解析单个菜谱详情页"""
        response = self.safe_request(detail_url)
        if not response:
            return None
        
        soup = BeautifulSoup(response.text, 'html.parser')
        
        # 1. 提取菜名
        title = None
        
        # 方法1：从隐藏input
        title_input = soup.find('input', id='recipe_title', type='hidden')
        if title_input and title_input.get('value'):
            title = title_input.get('value')
        else:
            # 方法2：从h1
            title_tag = soup.find('h1', class_='recipe_Subject')
            if title_tag:
                title = title_tag.text.strip()
            else:
                # 方法3：从title
                if soup.title and '的做法' in soup.title.string:
                    title = soup.title.string.split('的做法')[0].strip()
        
        if not title:
            print(f"     无法提取菜名: {detail_url}")
            return None
        
        title = title.replace('的做法', '').strip()
        
        # 2. 提取食材
        ingredients = []
        
        # 主要选择器
        ingredient_items = soup.select('fieldset.particulars li')
        
        for item in ingredient_items:
            name_tag = item.select_one('.category_s1 b') or item.select_one('.category_s1')
            amount_tag = item.select_one('.category_s2')
            group_tag = item.find_parent('fieldset')
            
            name = name_tag.text.strip() if name_tag else ''
            amount = amount_tag.text.strip() if amount_tag else '适量'
            group = group_tag.legend.text.strip() if group_tag and group_tag.legend else '主料'
            
            if name and len(name) > 1:
                ingredients.append({
                    'name': name,
                    'amount': amount,
                    'group': group
                })
        
        if not ingredients:
            print(f"     未找到食材: {title}")
            return None
        
        # 3. 提取步骤
        steps = []
        step_items = soup.select('.recipeStep li')
        
        for item in step_items:
            step_text = item.text.strip()
            if step_text:
                step_text = re.sub(r'^\d+[\.、]', '', step_text)
                if len(step_text) > 5:
                    steps.append(step_text)
        
        recipe_data = {
            'url': detail_url,
            'title': title,
            'ingredients': ingredients,
            'steps': steps,
            'ingredients_count': len(ingredients),
            'steps_count': len(steps),
            'crawl_time': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        }
        
        print(f"     成功: {title} (食材:{len(ingredients)}, 步骤:{len(steps)})")
        return recipe_data
    
    def crawl_cuisine(self, cuisine, url_template, max_pages=7):
        """爬取单个菜系"""
        print(f"\n{'='*60}")
        print(f"开始爬取【{cuisine}】，目标 {max_pages} 页")
        print(f"{'='*60}")
        
        cuisine_recipes = []
        empty_pages = 0
        
        for page in range(1, max_pages + 1):
            # 构造页面URL
            if page == 1:
                page_url = urljoin(self.base_url, url_template.replace('/page/{}/', ''))
            else:
                page_url = urljoin(self.base_url, url_template.format(page))
            
            print(f"\n📄 第 {page}/{max_pages} 页: {page_url}")
            
            # 获取列表页
            response = self.safe_request(page_url)
            if not response:
                empty_pages += 1
                if empty_pages >= 2:
                    print(f"   连续 {empty_pages} 页失败，停止爬取")
                    break
                continue
            
            # 提取详情页URL
            detail_urls = self.get_detail_urls_from_page(response.text, cuisine)
            print(f"   找到 {len(detail_urls)} 个菜谱链接")
            
            if not detail_urls:
                empty_pages += 1
                if empty_pages >= 2:
                    print(f"   连续 {empty_pages} 页无数据，停止爬取")
                    break
                continue
            
            empty_pages = 0
            
            # 爬取每个详情页
            page_recipes = []
            for i, detail_url in enumerate(detail_urls, 1):
                print(f"   [{i}/{len(detail_urls)}] 解析中...")
                recipe = self.parse_recipe_detail(detail_url)
                if recipe:
                    recipe['cuisine'] = cuisine
                    recipe['page'] = page
                    page_recipes.append(recipe)
                time.sleep(random.uniform(1, 2))
            
            cuisine_recipes.extend(page_recipes)
            print(f"   ✅ 本页完成: 成功 {len(page_recipes)}/{len(detail_urls)} 个")
            
            # 页间延迟
            if page < max_pages:
                wait = random.uniform(2, 4)
                print(f"   ⏳ 等待 {wait:.1f} 秒...")
                time.sleep(wait)
        
        print(f"\n📊 【{cuisine}】完成: 共 {len(cuisine_recipes)} 个菜谱")
        return cuisine_recipes
    
    def save_recipes(self, cuisine, recipes):
        """保存单个菜系的数据"""
        if not recipes:
            print(f"   {cuisine} 无新数据")
            return 0
        
        filename = os.path.join(self.save_dir, f'{cuisine}_recipes.json')
        
        # 读取已有数据
        existing_urls = set()
        if os.path.exists(filename):
            try:
                with open(filename, 'r', encoding='utf-8') as f:
                    existing_data = json.load(f)
                    existing_urls = {r['url'] for r in existing_data}
                print(f"   {cuisine} 已有 {len(existing_data)} 条")
            except:
                existing_data = []
        else:
            existing_data = []
        
        # 去重
        new_recipes = [r for r in recipes if r['url'] not in existing_urls]
        all_recipes = existing_data + new_recipes
        
        # 保存
        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(all_recipes, f, ensure_ascii=False, indent=2)
        
        print(f"   💾 保存完成: 新增 {len(new_recipes)} 条，累计 {len(all_recipes)} 条")
        return len(new_recipes)
    
    def run(self, pages=7):
        """
        运行爬虫
        pages: 每个菜系爬取页数 (默认7页，可以改成8)
        """
        print("\n" + "="*70)
        print("🍳 美食天下八大菜系爬虫 开始运行")
        print(f"📊 配置: 每个菜系 {pages} 页")
        print("="*70)
        
        start_time = time.time()
        total_new = 0
        
        for i, (cuisine, url_template) in enumerate(self.cuisines.items(), 1):
            print(f"\n{'='*60}")
            print(f"进度: {i}/8 - 处理【{cuisine}】")
            print(f"{'='*60}")
            
            # 爬取数据
            recipes = self.crawl_cuisine(cuisine, url_template, max_pages=pages)
            
            # 保存数据
            new_count = self.save_recipes(cuisine, recipes)
            total_new += new_count
            
            # 菜系间延迟
            if i < len(self.cuisines):
                wait = random.uniform(5, 8)
                print(f"\n⏳ 等待 {wait:.1f} 秒后处理下一个菜系...")
                time.sleep(wait)
        
        # 统计
        elapsed = time.time() - start_time
        print("\n" + "="*70)
        print("✅ 爬虫运行完成！")
        print("="*70)
        print(f"⏱️  总耗时: {elapsed/60:.1f} 分钟")
        print(f"📈 请求统计:")
        print(f"   - 总请求: {self.stats['total_requests']}")
        print(f"   - 成功: {self.stats['success']}")
        print(f"   - 失败: {self.stats['failed']}")
        print(f"   - 成功率: {self.stats['success']/self.stats['total_requests']*100:.1f}%")
        print(f"📦 数据统计:")
        print(f"   - 本次新增: {total_new} 条")
        
        # 显示各菜系最终数据量
        print(f"\n📊 各菜系数据量:")
        for cuisine in self.cuisines:
            filename = os.path.join(self.save_dir, f'{cuisine}_recipes.json')
            if os.path.exists(filename):
                with open(filename, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    print(f"   - {cuisine}: {len(data)} 条")
        
        print("\n✨ 下一步：运行 clean_data.py 进行清洗评分")


def main():
    """主函数"""
    # 创建爬虫实例，会自动使用 rag-service/meishichina_data
    crawler = MeiShiChinaCrawler()
    
    PAGES_TO_CRAWL = 25
    
    print(f"\n🎯 目标: 每个菜系爬取 {PAGES_TO_CRAWL} 页")
    print(f"数据保存目录: {crawler.save_dir}")
    print("预计每个菜系可获得 150-200 条菜谱")
    print(f"预计耗时: {PAGES_TO_CRAWL * 8 * 0.5:.0f}-{PAGES_TO_CRAWL * 8 * 1:.0f} 分钟")
    
    response = input("\n是否开始爬取？(y/n): ")
    if response.lower() == 'y':
        crawler.run(pages=PAGES_TO_CRAWL)
    else:
        print("已取消")

if __name__ == '__main__':
    main()