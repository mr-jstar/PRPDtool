with open('src/pipeline/PRPDPipeline.java', 'r', encoding='utf-8') as f:
    text = f.read()

import re

old_reader_catch = '''        } catch (Throwable ex) {
            running.set(false);
            readerFinished.set(true);
            boolean isInterrupted = ex instanceof InterruptedException || ex.getCause() instanceof InterruptedException;
            if (ex.getMessage() != null && ex.getMessage().contains("Interrupted before")) isInterrupted = true;
            if (!isInterrupted) {
                SwingUtilities.invokeLater(() -> listener.error(ex, " in readerLoop"));
            }
        }'''

new_reader_catch = '''        } catch (Throwable ex) {
            boolean wasRunning = running.getAndSet(false);
            readerFinished.set(true);
            if (wasRunning) {
                boolean isInterrupted = ex instanceof InterruptedException || ex.getCause() instanceof InterruptedException;
                if (ex.getMessage() != null && ex.getMessage().contains("Interrupted before")) isInterrupted = true;
                if (!isInterrupted) {
                    SwingUtilities.invokeLater(() -> listener.error(ex, " in readerLoop"));
                }
            }
        }'''
text = text.replace(old_reader_catch, new_reader_catch)

old_extractor_catch = '''        } catch (Throwable ex) {
            running.set(false);
            if (!(ex instanceof InterruptedException)) {
                SwingUtilities.invokeLater(() -> listener.error(ex, " in extractorLoop"));
            }
        }'''

new_extractor_catch = '''        } catch (Throwable ex) {
            boolean wasRunning = running.getAndSet(false);
            if (wasRunning) {
                if (!(ex instanceof InterruptedException)) {
                    SwingUtilities.invokeLater(() -> listener.error(ex, " in extractorLoop"));
                }
            }
        }'''
text = text.replace(old_extractor_catch, new_extractor_catch)

with open('src/pipeline/PRPDPipeline.java', 'w', encoding='utf-8') as f:
    f.write(text)
