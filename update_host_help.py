with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

old_host = '''            case "host" ->
                "IP address or DNS name of the Red Pitaya board.\\n"
                + "\\n"
                + "The rp_prpd_agent.py agent must be running on this board.";'''

new_host = '''            case "host" ->
                "IP address or DNS name of the Red Pitaya board.\\n"
                + "\\n"
                + "Tip: The default hostname is usually printed on the front connector of the physical board. "
                + "For example, if your board shows 'f0f771', the host name is 'rp-f0f771.local'.\\n"
                + "\\n"
                + "Note: The rp_prpd_agent.py agent must be running on this board.";'''

text = text.replace(old_host, new_host)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
