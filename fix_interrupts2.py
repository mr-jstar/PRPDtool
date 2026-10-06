with open('src/pipeline/PRPDPipeline.java', 'r', encoding='utf-8') as f:
    text = f.read()

old_reader_catch = '''        } catch (Throwable ex) {
            running.set(false);
            readerFinished.set(true);
            if (!(ex instanceof InterruptedException) && !(ex.getCause() instanceof InterruptedException) && !ex.getMessage().contains("Interrupted before")) {
                SwingUtilities.invokeLater(() -> listener.error(ex, " in readerLoop"));
            }
        }'''

new_reader_catch = '''        } catch (Throwable ex) {
            running.set(false);
            readerFinished.set(true);
            boolean isInterrupted = ex instanceof InterruptedException || ex.getCause() instanceof InterruptedException;
            if (ex.getMessage() != null && ex.getMessage().contains("Interrupted before")) isInterrupted = true;
            if (!isInterrupted) {
                SwingUtilities.invokeLater(() -> listener.error(ex, " in readerLoop"));
            }
        }'''
text = text.replace(old_reader_catch, new_reader_catch)

with open('src/pipeline/PRPDPipeline.java', 'w', encoding='utf-8') as f:
    f.write(text)
