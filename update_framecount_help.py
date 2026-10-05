with open('src/prpdtool/PRPDTool.java', 'r', encoding='utf-8') as f:
    text = f.read()

old_fc = '''            case "frameCount" ->
                "Number of frames collected in frame-count mode.";'''

new_fc = '''            case "frameCount" ->
                "Number of frames (data chunks) to collect per acquisition.\\n"
                + "\\n"
                + "Behavior depends on the selected Mode:\\n"
                + "- 'frames' mode: You set this value manually. The total number of recorded samples will exactly equal (Frame count * Frame size).\\n"
                + "- 'duration' mode: This value is auto-calculated. It shows the minimum number of frames required to cover the requested Duration. For example, if your duration requires 100,000 samples and frame size is 65,536, it must fetch 2 frames.";'''

text = text.replace(old_fc, new_fc)

with open('src/prpdtool/PRPDTool.java', 'w', encoding='utf-8') as f:
    f.write(text)
