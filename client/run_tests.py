import subprocess
import time
import os
import json
import sys
import random
import shutil

# --- הגדרות ה-STRESS ---
NUM_REGULAR_USERS = 10    # משתמשים שרואים משחק אחד
NUM_HEAVY_USERS = 5       # משתמשים שרואים את *כל* המשחקים במקביל
NUM_FLAKY_USERS = 5       # משתמשים שנכנסים ויוצאים מערוצים לפני המשחק
EVENTS_PER_GAME = 100     # כמות אירועים בכל משחק (סה"כ 300 אירועים במערכת)
CLIENT_EXE = "./bin/StompWCIClient"
HOST = "127.0.0.1"
PORT = 7777
DATA_DIR = "stress_data"

# צבעים
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
CYAN = "\033[96m"
RESET = "\033[0m"

def print_status(message, status):
    color = GREEN if status else RED
    result = "PASS" if status else "FAIL"
    print(f"[{color}{result}{RESET}] {message}")

def ensure_clean_dir():
    if os.path.exists(DATA_DIR):
        shutil.rmtree(DATA_DIR)
    os.makedirs(DATA_DIR)

# --- יצירת משחקים ---

def generate_game(team_a, team_b, num_events):
    game_name = f"{team_a}_{team_b}"
    events = []
    expected_stats = {
        "active": "true", 
        "before halftime": "true",
        "goals_a": 0,
        "goals_b": 0,
        "events_count": 0
    }

    # Kickoff
    events.append({
        "event name": "kickoff", "time": 0,
        "general game updates": {"active": "true", "before halftime": "true"},
        "team a updates": {}, "team b updates": {},
        "description": "Game Start"
    })

    current_time = 0
    for i in range(1, num_events):
        current_time += random.randint(10, 100)
        etype = random.choice(["Goal", "Foul", "Offside"])
        
        event = {
            "event name": f"{etype} {i}", "time": current_time,
            "general game updates": {}, "team a updates": {}, "team b updates": {},
            "description": f"Desc {i}"
        }

        if etype == "Goal":
            if random.random() > 0.5:
                expected_stats["goals_a"] += 1
                event["team a updates"]["goals"] = str(expected_stats["goals_a"])
            else:
                expected_stats["goals_b"] += 1
                event["team b updates"]["goals"] = str(expected_stats["goals_b"])
        
        events.append(event)

    # Final Whistle
    events.append({
        "event name": "final whistle", "time": current_time + 60,
        "general game updates": {"active": "false"},
        "team a updates": {}, "team b updates": {},
        "description": "Game Over"
    })
    
    expected_stats["active"] = "false"
    expected_stats["events_count"] = len(events)

    data = {"team a": team_a, "team b": team_b, "events": events}
    fpath = os.path.join(DATA_DIR, f"{game_name}.json")
    with open(fpath, 'w') as f:
        json.dump(data, f, indent=4)

    return game_name, fpath, expected_stats

# --- מחלקת ניהול קליינט ---

class ClientRunner:
    def __init__(self, username, password="123"):
        self.username = username
        self.password = password
        self.process = None
        self.subscribed_games = []
    
    def start(self):
        self.process = subprocess.Popen(
            [CLIENT_EXE],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1 # Line buffered
        )

    def send(self, cmd):
        if self.process and self.process.stdin:
            try:
                self.process.stdin.write(cmd + "\n")
                self.process.stdin.flush()
            except BrokenPipeError:
                print(f"{RED}Error: Client {self.username} crashed!{RESET}")

    def login(self):
        self.send(f"login {HOST}:{PORT} {self.username} {self.password}")

    def join(self, game_name):
        self.send(f"join {game_name}")
        self.subscribed_games.append(game_name)

    def exit_channel(self, game_name):
        self.send(f"exit {game_name}")
        if game_name in self.subscribed_games:
            self.subscribed_games.remove(game_name)

    def report(self, json_path):
        self.send(f"report {json_path}")

    def request_summary(self, game_name, reporter_user, out_path):
        self.send(f"summary {game_name} {reporter_user} {out_path}")

    def logout(self):
        self.send("logout")

    def wait_finish(self):
        if self.process:
            try:
                self.process.communicate(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()

# --- Main Logic ---

def run_super_stress():
    print(f"{CYAN}=== SUPER STRESS TEST INITIATED ==={RESET}")
    print(f"Configuration: {EVENTS_PER_GAME} Events/Game | {NUM_REGULAR_USERS + NUM_HEAVY_USERS + NUM_FLAKY_USERS} Total Users")
    
    ensure_clean_dir()

    # 1. הכנת המשחקים
    games = []
    games.append(generate_game("Germany", "Spain", EVENTS_PER_GAME))
    games.append(generate_game("Brazil", "Argentina", EVENTS_PER_GAME))
    games.append(generate_game("Italy", "France", EVENTS_PER_GAME))
    
    # מיפוי שם משחק -> אובייקט נתונים
    game_map = {g[0]: g for g in games}

    clients = []

    try:
        # 2. אתחול משתמשים וחיבור (Phase 1: Setup)
        print("\n--- Phase 1: Launching Clients & Subscribing ---")
        
        # A. משתמשים רגילים (מתחלקים בין המשחקים)
        for i in range(NUM_REGULAR_USERS):
            c = ClientRunner(f"RegUser_{i}")
            c.start()
            c.login()
            # מחלקים אותם למשחקים שונים (Round Robin)
            game_to_join = games[i % len(games)][0]
            c.join(game_to_join)
            clients.append(c)

        # B. משתמשים כבדים (נרשמים להכל)
        for i in range(NUM_HEAVY_USERS):
            c = ClientRunner(f"HeavyUser_{i}")
            c.start()
            c.login()
            for g in games:
                c.join(g[0])
            clients.append(c)

        # C. משתמשים "מתלבטים" (מקרי קצה של Subscribe/Unsubscribe)
        for i in range(NUM_FLAKY_USERS):
            c = ClientRunner(f"FlakyUser_{i}")
            c.start()
            c.login()
            # מצטרף למשחק הראשון
            c.join(games[0][0])
            time.sleep(0.1)
            # מתחרט ויוצא!
            c.exit_channel(games[0][0])
            time.sleep(0.1)
            # מצטרף למשחק השני (וזה מה שצריך לתפוס)
            c.join(games[1][0])
            clients.append(c)

        print(f"{GREEN}All {len(clients)} clients connected and subscribed.{RESET}")
        time.sleep(2) # זמן קצר לשרת לעבד רישומים

        # 3. הפעלת מדווחים במקביל (Phase 2: Parallel Reporting)
        print("\n--- Phase 2: Unleashing Parallel Reporters ---")
        reporters = []
        for g_name, json_path, _ in games:
            r_user = f"Reporter_{g_name}"
            r = ClientRunner(r_user)
            r.start()
            r.login()
            r.join(g_name)
            # הפעלת הדיווח (זה יקרה במקביל עבור כל ה-3!)
            r.report(json_path)
            r.logout() # מתנתק מיד אחרי הדיווח
            reporters.append(r)

        # המתנה לסיום המדווחים
        print(f"{YELLOW}Reporters are running concurrently...{RESET}")
        for r in reporters:
            r.wait_finish()
        
        # זמן הפצה (Propagation)
        # מכיוון שיש הרבה אירועים והרבה משתמשים, ניתן לשרת קצת אוויר
        wait_time = max(5, EVENTS_PER_GAME * 0.1)
        print(f"Waiting {wait_time:.1f}s for messages to arrive...")
        time.sleep(wait_time)

        # 4. איסוף סיכומים (Phase 3: Summaries)
        print("\n--- Phase 3: Collecting Summaries ---")
        summary_files_map = [] # (client_obj, game_name, file_path)

        for c in clients:
            for g_name in c.subscribed_games:
                # ה-Reporter עבור כל משחק נקרא Reporter_{GameName}
                r_user = f"Reporter_{g_name}"
                out_path = os.path.join(DATA_DIR, f"summary_{c.username}_{g_name}.txt")
                c.request_summary(g_name, r_user, out_path)
                summary_files_map.append((c, g_name, out_path))
            
            c.logout() # סיימנו עם המשתמש הזה

        # נותנים לקליינטים רגע לכתוב את הקבצים ולצאת
        for c in clients:
            c.wait_finish()

    except Exception as e:
        print(f"{RED}CRITICAL ERROR: {e}{RESET}")
        import traceback
        traceback.print_exc()
        # ניקוי
        for c in clients: 
            if c.process: c.process.kill()
        return

    # 5. אימות (Phase 4: Verification)
    print("\n--- Phase 4: Verification ---")
    total_checks = 0
    passed_checks = 0

    for client_obj, game_name, fpath in summary_files_map:
        total_checks += 1
        expected = game_map[game_name][2] # ה-Stats שחישבנו מראש
        
        if not os.path.exists(fpath):
            print_status(f"Missing file for {client_obj.username} on {game_name}", False)
            continue
        
        with open(fpath, 'r') as f:
            content = f.read()
        
        # בדיקות קריטיות
        is_ok = True
        
        # 1. בדיקת סה"כ אירועים (מוודא שלא הלכו לאיבוד הודעות)
        # אנחנו סופרים כמה פעמים מופיע 'event name' או לפי תיאורים, 
        # אבל הכי פשוט: לוודא שהסטטיסטיקה הסופית נכונה.
        
        if f"active: {expected['active']}" not in content:
            is_ok = False
            print(f"  > {client_obj.username} [{game_name}]: Wrong 'active' status")

        # בדיקת גולים (אם היו)
        if expected["goals_a"] > 0:
            if f"goals: {expected['goals_a']}" not in content:
                is_ok = False
                print(f"  > {client_obj.username} [{game_name}]: Wrong Goals A")
        
        # בדיקת ערבוב (Cross Contamination Check)
        # אם אני במשחק Germany_Spain, אסור לי לראות Brazil!
        other_games = [g[0] for g in games if g[0] != game_name]
        for og in other_games:
            # מפרקים את השם כדי למצוא את הקבוצות
            parts = og.split('_')
            if parts[0] in content or parts[1] in content:
                 # זהירות: לפעמים שמות דומים, אבל כאן השמות שונים לגמרי
                 is_ok = False
                 print(f"{RED}  > POLLUTION DETECTED!{RESET} {client_obj.username} saw {og} info inside {game_name} summary!")

        if is_ok:
            passed_checks += 1
        else:
            print_status(f"Summary failed for {client_obj.username} on {game_name}", False)

    print("-" * 30)
    print(f"Total Summaries Checked: {total_checks}")
    print(f"Passed: {passed_checks}")
    
    if passed_checks == total_checks:
        print(f"\n{GREEN}>>> ULTIMATE STRESS TEST PASSED <<< {RESET}")
    else:
        print(f"\n{RED}>>> STRESS TEST FAILED ({total_checks - passed_checks} errors) <<<{RESET}")

if __name__ == "__main__":
    if not os.path.exists(CLIENT_EXE):
        print(f"{RED}Error: Client executable not found at {CLIENT_EXE}{RESET}")
        sys.exit(1)
    run_super_stress()