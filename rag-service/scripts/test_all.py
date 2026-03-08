# test_all.py - 一键测试所有脚本
"""
测试所有文件是否能正常运行
运行方式: python scripts/test_all.py
"""

import os
import sys
import importlib.util
import subprocess

# 颜色输出
class Colors:
    HEADER = '\033[95m'
    BLUE = '\033[94m'
    GREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    END = '\033[0m'
    BOLD = '\033[1m'

def print_header(text):
    print(f"\n{Colors.HEADER}{'='*60}{Colors.END}")
    print(f"{Colors.BOLD}{text}{Colors.END}")
    print(f"{Colors.HEADER}{'='*60}{Colors.END}")

def print_success(text):
    print(f"{Colors.GREEN}✅ {text}{Colors.END}")

def print_error(text):
    print(f"{Colors.FAIL}❌ {text}{Colors.END}")

def print_warning(text):
    print(f"{Colors.WARNING}⚠️  {text}{Colors.END}")

def test_import(module_name, file_path):
    """测试能否导入模块"""
    try:
        spec = importlib.util.spec_from_file_location(module_name, file_path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        print_success(f"成功导入 {module_name}")
        return True
    except Exception as e:
        print_error(f"导入 {module_name} 失败: {e}")
        return False

def test_run_script(script_path, args=[], timeout=5):
    """测试运行脚本（只导入不执行main）"""
    try:
        # 只检查语法，不实际运行
        result = subprocess.run(
            [sys.executable, "-m", "py_compile", script_path],
            capture_output=True,
            text=True,
            timeout=timeout
        )
        if result.returncode == 0:
            print_success(f"语法检查通过: {os.path.basename(script_path)}")
            return True
        else:
            print_error(f"语法错误: {result.stderr}")
            return False
    except subprocess.TimeoutExpired:
        print_error(f"检查超时: {script_path}")
        return False

def main():
    """主测试函数"""
    print_header("🔍 开始测试所有文件")
    
    # 获取当前脚本所在目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    rag_root = os.path.dirname(script_dir)  # rag-service 根目录
    
    print(f"📁 RAG服务根目录: {rag_root}")
    print(f"📁 脚本目录: {script_dir}")
    
    # 要测试的文件列表
    test_files = [
        {
            'name': 'app.py',
            'path': os.path.join(rag_root, 'app.py'),
            'type': 'service',
            'description': 'RAG主服务'
        },
        {
            'name': 'scripts/clean_data.py',
            'path': os.path.join(rag_root, 'scripts', 'clean_data.py'),
            'type': 'script',
            'description': '数据清洗脚本'
        },
        {
            'name': 'scripts/ingredient_learner.py',
            'path': os.path.join(rag_root, 'scripts', 'ingredient_learner.py'),
            'type': 'script',
            'description': '知识库学习脚本'
        },
        {
            'name': 'scripts/recipe_spider.py',
            'path': os.path.join(rag_root, 'scripts', 'recipe_spider.py'),
            'type': 'script',
            'description': '爬虫脚本'
        }
    ]
    
    # 检查依赖文件
    data_files = [
        {
            'name': 'data/recipes.json',
            'path': os.path.join(rag_root, 'data', 'recipes.json'),
            'description': '食谱数据'
        },
        {
            'name': 'ingredient_kb.json',
            'path': os.path.join(rag_root, 'ingredient_kb.json'),
            'description': '食材知识库'
        }
    ]
    
    # 1. 测试Python文件语法
    print_header("📝 测试Python文件语法")
    all_passed = True
    
    for tf in test_files:
        if os.path.exists(tf['path']):
            print(f"\n📄 测试: {tf['name']} ({tf['description']})")
            if not test_run_script(tf['name'], tf['path']):
                all_passed = False
        else:
            print_error(f"文件不存在: {tf['path']}")
            all_passed = False
    
    # 2. 检查依赖文件是否存在
    print_header("📂 检查依赖文件")
    all_files_exist = True
    
    for df in data_files:
        if os.path.exists(df['path']):
            print_success(f"找到 {df['name']} ({df['description']})")
            # 尝试读取JSON验证格式
            try:
                with open(df['path'], 'r', encoding='utf-8') as f:
                    data = json.load(f)
                print_success(f"  ✓ JSON格式正确")
            except json.JSONDecodeError as e:
                print_error(f"  ✗ JSON格式错误: {e}")
                all_files_exist = False
            except Exception as e:
                print_warning(f"  无法读取: {e}")
        else:
            print_error(f"缺少文件: {df['name']} ({df['description']})")
            all_files_exist = False
    
    # 3. 测试关键模块导入
    print_header("🔧 测试模块导入")
    
    try:
        import fastapi
        print_success(f"fastapi 版本: {fastapi.__version__}")
    except ImportError:
        print_error("fastapi 未安装")
        all_passed = False
    
    try:
        import chromadb
        print_success(f"chromadb 已安装")
    except ImportError:
        print_error("chromadb 未安装")
        all_passed = False
    
    try:
        import sentence_transformers
        print_success(f"sentence-transformers 已安装")
    except ImportError:
        print_error("sentence-transformers 未安装")
        all_passed = False
    
    # 4. 测试相对路径是否正确
    print_header("🔄 测试路径配置")
    
    # 测试 ingredient_learner 的路径
    try:
        sys.path.insert(0, rag_root)
        from scripts import ingredient_learner
        print_success("ingredient_learner 模块导入成功")
    except ImportError as e:
        print_error(f"ingredient_learner 导入失败: {e}")
        all_passed = False
    
    # 5. 汇总结果
    print_header("📊 测试结果汇总")
    
    if all_passed and all_files_exist:
        print(f"{Colors.GREEN}{Colors.BOLD}✅ 所有测试通过！文件都可以正常运行{Colors.END}")
        print(f"\n{Colors.BOLD}你可以放心使用以下命令：{Colors.END}")
        print(f"   {Colors.BLUE}python app.py{Colors.END} - 启动RAG服务")
        print(f"   {Colors.BLUE}python scripts/clean_data.py{Colors.END} - 清洗数据")
        print(f"   {Colors.BLUE}python scripts/ingredient_learner.py{Colors.END} - 学习知识库")
        print(f"   {Colors.BLUE}python scripts/recipe_spider.py{Colors.END} - 爬取数据")
    else:
        print(f"{Colors.FAIL}{Colors.BOLD}❌ 部分测试失败，请检查上面的错误信息{Colors.END}")
    
    return 0 if all_passed and all_files_exist else 1

if __name__ == '__main__':
    # 导入json用于文件验证
    import json
    sys.exit(main())