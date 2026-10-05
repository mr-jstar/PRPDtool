with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

old_port = '''            case "port" ->
                "TCP port of the agent running on Red Pitaya.\\n"
                + "\\n"
                + "It must be the same as the agent's --port parameter.";'''

new_port = '''            case "port" ->
                "TCP network port used to communicate with the Red Pitaya board.\\n"
                + "\\n"
                + "This value must exactly match the port number configured in the Python agent running on the board (the --port parameter).\\n"
                + "\\n"
                + "Default value: 9999";'''

text = text.replace(old_port, new_port)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
