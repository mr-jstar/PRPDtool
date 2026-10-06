with open('src/pipeline/PRPDPipeline.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Fix readerLoop
old_reader_catch = '''        } catch (Throwable ex) {
            running.set(false);
            readerFinished.set(true);
            SwingUtilities.invokeLater(() -> listener.error(ex, " in readerLoop"));
        }'''

new_reader_catch = '''        } catch (Throwable ex) {
            running.set(false);
            readerFinished.set(true);
            if (!(ex instanceof InterruptedException) && !(ex.getCause() instanceof InterruptedException) && !ex.getMessage().contains("Interrupted before")) {
                SwingUtilities.invokeLater(() -> listener.error(ex, " in readerLoop"));
            }
        }'''
text = text.replace(old_reader_catch, new_reader_catch)

# Fix extractorLoop
old_extractor_catch = '''        } catch (Throwable ex) {
            running.set(false);
            SwingUtilities.invokeLater(() -> listener.error(ex, " in extractorLoop"));
        }'''

new_extractor_catch = '''        } catch (Throwable ex) {
            running.set(false);
            if (!(ex instanceof InterruptedException)) {
                SwingUtilities.invokeLater(() -> listener.error(ex, " in extractorLoop"));
            }
        }'''
text = text.replace(old_extractor_catch, new_extractor_catch)

with open('src/pipeline/PRPDPipeline.java', 'w', encoding='utf-8') as f:
    f.write(text)
