import os
import subprocess
import sys
import json

def check():
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')
    script_dir = os.path.dirname(os.path.abspath(__file__))
    agent_dir = os.path.abspath(os.path.join(script_dir, ".."))
    cli_path = os.path.join(agent_dir, "context-mode", "cli.bundle.mjs")
    
    print("=== KIEM TRA MOI TRUONG CONTEXT-MODE STANDALONE ===")
    
    # 1. Kiểm tra file runtime
    if not os.path.exists(cli_path):
        print(f"[FAIL] Không tìm thấy runtime bundle tại: {cli_path}")
        return False
    print(f"[PASS] Runtime bundle tồn tại: {cli_path}")
    
    # 2. Kiểm tra node command
    try:
        res = subprocess.run(["node", cli_path, "doctor"], capture_output=True, text=True, timeout=15)
        if res.returncode == 0 and "context-mode doctor" in res.stdout:
            print("[PASS] context-mode doctor chạy thành công!")
        else:
            print(f"[WARN] Doctor trả về code {res.returncode}: {res.stderr[:200]}")
    except Exception as e:
        print(f"[FAIL] Lỗi thực thi node: {e}")
        return False
        
    # 3. Kiểm tra mcp_config.json
    mcp_config_path = os.path.join(agent_dir, "mcp_config.json")
    if os.path.exists(mcp_config_path):
        with open(mcp_config_path, "r", encoding="utf-8") as f:
            data = json.load(f)
            if "context-mode" in data.get("mcpServers", {}):
                print("[PASS] Đã cấu hình context-mode trong mcp_config.json")
            else:
                print("[WARN] Chưa thấy context-mode trong mcpServers")
                
    print("=== TẤT CẢ KIỂM TRA CONTEXT-MODE HOÀN TẤT ===")
    return True

if __name__ == "__main__":
    success = check()
    sys.exit(0 if success else 1)
