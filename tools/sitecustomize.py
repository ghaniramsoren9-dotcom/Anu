import sys
from pathlib import Path

if Path(sys.argv[0]).name == "apply_conversation_history.py":
    p = Path(sys.argv[0]).resolve().parent.parent / "app/src/main/java/com/ghaniram/zoya/ZoyaSessionManager.kt"
    if p.exists():
        s = p.read_text(encoding="utf-8")
        changed = False
        if 'val githubTool = JSONObject().put("name", "githubAction")' not in s:
            anchor = '        val screen = JSONObject().put("name", "readScreen")'
            github = '        val githubTool = JSONObject().put("name", "githubAction").put("description", "Read or modify the connected GitHub repository. Use this for explicit repository/file/commit requests. Never claim success unless the tool returns a successful GitHub result.").put("parameters", JSONObject().apply {\n            put("type", "object")\n            put("properties", JSONObject().apply {\n                put("action", prop("string", "GitHub operation: test, list_repos, read, write, create, or delete."))\n                put("repo", prop("string", "Repository in owner/name form. Leave blank to use Anu\\'s configured default repository."))\n                put("path", prop("string", "Repository file path for read/write/create/delete operations."))\n                put("content", prop("string", "Complete UTF-8 file content for write/create operations."))\n                put("message", prop("string", "Commit message for write/create/delete operations."))\n            })\n            put("required", JSONArray().put("action"))\n        })\n'
            if anchor in s:
                s=s.replace(anchor,github+anchor,1); changed=True
        old='return JSONArray().put(phone).put(appTool).put(web).put(access).put(screen).put(device).put(taskTool)'
        new='return JSONArray().put(phone).put(appTool).put(web).put(access).put(screen).put(device).put(taskTool).put(githubTool)'
        if old in s:
            s=s.replace(old,new,1); changed=True
        if '"githubAction", "github", "githubCommit", "githubRepo" ->' not in s:
            anchor='        "getDeviceInfo" -> {'
            case = '        "githubAction", "github", "githubCommit", "githubRepo" -> {\n            val action = args.optString("action").ifBlank { args.optString("command") }.lowercase().trim()\n            val repo = args.optString("repo").ifBlank { args.optString("repository") }.trim()\n            val path = args.optString("path").ifBlank { args.optString("file") }.trim()\n            val content = args.optString("content").ifBlank { args.optString("code") }\n            val message = args.optString("message").ifBlank { "Commit from Anu" }\n            phoneControls.githubAction(action, repo, path, content, message)\n        }\n'
            if anchor in s:
                s=s.replace(anchor,case+anchor,1); changed=True
        if changed:
            p.write_text(s, encoding="utf-8")
            print("sitecustomize: GitHub tool bridge injected")
