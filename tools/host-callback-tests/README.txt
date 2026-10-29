Deterministic host regression for the actual production InputLogicHandler.
Android Handler/Looper, editor and suggestion collaborators are TEST-ONLY facades
under tools/, outside app/src and APK source sets. They model queue draining and
late completion without any Android runtime, editor IPC, device, or personal text.
The production handler is compiled unchanged by the runner. This does not qualify
Android Looper scheduling, JNI, gesture recognition, or actual device behavior.
Set JAVA_HOME and PETAL_CALLBACK_TEST_OUTPUT, then run bash tools/host-callback-tests/run.sh.
