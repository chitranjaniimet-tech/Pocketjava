package com.moneyclaritytech.pocketforge;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/** Phone-first clean-room Java learning IDE. */
public final class MainActivity extends AppCompatActivity {
    private static final int REQ_IMPORT_JAVA = 5001;
    private static final String PREF_THEME_STYLE = "theme_style";

    private CodeEditor editor;
    private ViewFlipper pages;
    private LinearLayout tabRow, symbolBar, learnContainer, toolsContainer, settingsContainer;
    private TextView subtitle, consoleText, consolePreview;
    private ScrollView consoleScroll;
    private EditText terminalInput;
    private MaterialButton btnRun, btnRunTop;
    private ProjectStore store;
    private TerminalEngine terminal;
    private MavenResolver maven;
    private File currentFile;
    private boolean dirty, running, loadingEditor;
    private final ExecutorService ioWorker = Executors.newSingleThreadExecutor();
    private final StringBuilder replBody = new StringBuilder();
    private SharedPreferences prefs;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        applySavedThemeStyle();
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_main);
        bindViews();
        configureSystemBars();
        prefs = getSharedPreferences("ui", MODE_PRIVATE);
        store = new ProjectStore(this);
        terminal = new TerminalEngine(store);
        maven = new MavenResolver(this);
        configureEditor();
        configureNavigation();
        configureActions();
        buildSymbolBar();
        buildLessons();
        buildTools();
        buildSettings();
        initializeProject();
        handleViewIntent(getIntent());
    }

    private void bindViews() {
        editor = findViewById(R.id.editor); pages = findViewById(R.id.pages);
        tabRow = findViewById(R.id.tabRow); symbolBar = findViewById(R.id.symbolBar);
        learnContainer = findViewById(R.id.learnContainer); toolsContainer = findViewById(R.id.toolsContainer); settingsContainer = findViewById(R.id.settingsContainer);
        subtitle = findViewById(R.id.subtitle); consoleText = findViewById(R.id.consoleText);
        consolePreview = findViewById(R.id.consolePreview); consoleScroll = findViewById(R.id.consoleScroll);
        terminalInput = findViewById(R.id.terminalInput); btnRun = findViewById(R.id.btnRun); btnRunTop = findViewById(R.id.btnRunTop);
    }

    private void configureSystemBars() {
        boolean dark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat bars = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(!dark);
        bars.setAppearanceLightNavigationBars(!dark);
    }

    private void configureEditor() {
        editor.setEditorLanguage(new JavaLanguage());
        editor.setTypefaceText(Typeface.MONOSPACE);
        boolean dark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        EditorColorScheme s = editor.getColorScheme();
        s.setColor(EditorColorScheme.WHOLE_BACKGROUND, dark ? 0xFF12141A : 0xFFF8F9FD);
        s.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, dark ? 0xFF12141A : 0xFFF8F9FD);
        s.setColor(EditorColorScheme.TEXT_NORMAL, dark ? 0xFFF0F2F7 : 0xFF20242C);
        s.setColor(EditorColorScheme.LINE_NUMBER, dark ? 0xFF7F8797 : 0xFF7A8090);
        s.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, dark ? 0xFFE8ECF6 : 0xFF20242C);
        s.setColor(EditorColorScheme.KEYWORD, dark ? 0xFF8AB4F8 : 0xFF234DDC);
        s.setColor(EditorColorScheme.LITERAL, dark ? 0xFF7DDBC7 : 0xFF007A68);
        s.setColor(EditorColorScheme.COMMENT, dark ? 0xFF8B93A5 : 0xFF6D7482);
        s.setColor(EditorColorScheme.OPERATOR, dark ? 0xFFAEC6FF : 0xFF2849B8);
        s.setColor(EditorColorScheme.FUNCTION_NAME, dark ? 0xFFE9A7F3 : 0xFF8D2A9B);
        optional(editor, "setTextSize", new Class<?>[]{float.class}, (float) prefs.getInt("font", 15));
        optional(editor, "setWordwrap", new Class<?>[]{boolean.class}, prefs.getBoolean("wrap", false));
        optional(editor, "setLineNumberEnabled", new Class<?>[]{boolean.class}, prefs.getBoolean("lines", true));
        editor.setFocusableInTouchMode(true);
        editor.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showEditorKeyboard();
            else if (!loadingEditor) markDirty();
        });
        editor.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) showEditorKeyboard();
            return false;
        });
    }

    private void configureNavigation() {
        findViewById(R.id.navEditor).setOnClickListener(v -> showPage(0));
        findViewById(R.id.navLearn).setOnClickListener(v -> showPage(1));
        findViewById(R.id.navConsole).setOnClickListener(v -> showPage(2));
        findViewById(R.id.navTools).setOnClickListener(v -> showPage(3));
        showPage(0);
    }

    private void configureActions() {
        btnRun.setOnClickListener(v -> runActiveSource()); btnRunTop.setOnClickListener(v -> runActiveSource());
        findViewById(R.id.btnKeyboard).setOnClickListener(v -> showEditorKeyboard());
        findViewById(R.id.btnOpenConsole).setOnClickListener(v -> showPage(2));
        findViewById(R.id.btnFiles).setOnClickListener(v -> showFilesDialog());
        findViewById(R.id.btnFormat).setOnClickListener(v -> formatActive());
        findViewById(R.id.btnMore).setOnClickListener(this::showMoreMenu);
        findViewById(R.id.btnClearConsole).setOnClickListener(v -> { consoleText.setText(""); consolePreview.setText("Console cleared"); });
        findViewById(R.id.btnTerminalSend).setOnClickListener(v -> executeTerminal());
        terminalInput.setOnEditorActionListener((v, actionId, event) -> { if (actionId == EditorInfo.IME_ACTION_DONE) { executeTerminal(); return true; } return false; });
    }

    private void initializeProject() {
        try { store.ensureStarter(); List<File> files = store.listJavaFiles(); if (!files.isEmpty()) openFile(files.get(0)); }
        catch (Exception e) { toast("Could not create project: " + e.getMessage()); }
    }

    private void buildSymbolBar() {
        String[] keys = {"{", "}", "(", ")", "[", "]", ";", "\"", "'", "=", "+", "-", "*", "/", "<", ">", "!", "&", "|", ".", ","};
        symbolBar.removeAllViews();
        for (String key : keys) { MaterialButton b = smallButton(key); b.setMinWidth(dp(44)); b.setOnClickListener(v -> insertAtCursor(key)); symbolBar.addView(b); }
    }

    private void buildLessons() {
        learnContainer.removeAllViews(); learnContainer.addView(heading("Learn Java — from zero"));
        learnContainer.addView(body("Short lessons built for a phone: read the idea, load the code, run it, compare the result, then try one tiny challenge."));
        for (Lesson lesson : LessonRepository.beginnerLessons()) {
            MaterialCardView card = card(); LinearLayout box = column(dp(14)); TextView title = heading(lesson.title); title.setTextSize(17);
            box.addView(title); box.addView(body(lesson.concept)); MaterialButton open = actionButton("Open lesson"); open.setOnClickListener(v -> showLesson(lesson)); box.addView(open); card.addView(box); learnContainer.addView(card, cardParams());
        }
    }

    private void showLesson(Lesson lesson) {
        LinearLayout box = column(dp(16)); box.addView(body(lesson.concept)); box.addView(codeBlock(lesson.code)); box.addView(label("Expected result")); box.addView(codeBlock(lesson.expected)); box.addView(label("Tiny challenge")); box.addView(body(lesson.challenge));
        new MaterialAlertDialogBuilder(this).setTitle(lesson.title).setView(wrapScroll(box)).setNegativeButton("Close", null)
                .setNeutralButton("Copy code", (d, w) -> copy(lesson.code)).setPositiveButton("Load in editor", (d, w) -> { setEditorText(lesson.code); dirty = true; updateSubtitle(); showPage(0); }).show();
    }

    private void buildTools() {
        toolsContainer.removeAllViews(); toolsContainer.addView(heading("Tools"));
        toolsContainer.addView(body("Useful IDE features arranged for a phone instead of a desktop-sized screen."));
        addTool("Language hub", "Choose a language track, see what runs on-device, and follow the module roadmap.", this::showLanguageHub);
        addTool("Examples", "Original PocketForge examples for loops, input, arrays, methods and objects.", this::showExamples);
        addTool("Java REPL", "Try short Java statements and keep earlier statements in the session.", this::showRepl);
        addTool("Maven libraries", "Add common Maven Central JARs with group:artifact:version.", this::showMaven);
        addTool("Project files", "Create, import, rename, switch and delete Java files.", this::showFilesDialog);
        addTool("Settings & preferences", "A dedicated screen for code size, layout, keyboard behaviour and appearance.", () -> showPage(4));
        addTool("Console & shell", "Learner commands plus normal Android shell commands inside the app sandbox.", () -> showPage(2));
        addTool("Editor settings", "Font size, word wrap, line numbers and appearance.", this::showEditorSettings);
        addTool("Java quick docs", "Offline reminders for common Java syntax and classes.", this::showDocs);
        addTool("Compatibility & licenses", "See exactly what this build implements.", this::showCompatibility);
    }

    private void buildSettings() {
        settingsContainer.removeAllViews();
        settingsContainer.addView(heading("Settings"));
        settingsContainer.addView(body("Make PocketForge comfortable for your phone and the way you learn. Changes are saved automatically."));

        MaterialCardView editorCard = card();
        LinearLayout editorBox = column(dp(14));
        editorBox.addView(heading("Editor"));
        editorBox.addView(body("Choose a readable coding layout."));
        SwitchMaterial wrap = new SwitchMaterial(this);
        wrap.setText("Word wrap");
        wrap.setChecked(prefs.getBoolean("wrap", false));
        wrap.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("wrap", checked).apply();
            optional(editor, "setWordwrap", new Class<?>[]{boolean.class}, checked);
        });
        editorBox.addView(wrap);
        SwitchMaterial lines = new SwitchMaterial(this);
        lines.setText("Show line numbers");
        lines.setChecked(prefs.getBoolean("lines", true));
        lines.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("lines", checked).apply();
            optional(editor, "setLineNumberEnabled", new Class<?>[]{boolean.class}, checked);
        });
        editorBox.addView(lines);
        TextView fontLabel = body("Code size: " + prefs.getInt("font", 15) + " sp");
        editorBox.addView(fontLabel);
        SeekBar font = new SeekBar(this);
        font.setMax(12);
        font.setProgress(prefs.getInt("font", 15) - 12);
        font.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = 12 + progress;
                fontLabel.setText("Code size: " + size + " sp");
                if (fromUser) {
                    prefs.edit().putInt("font", size).apply();
                    optional(editor, "setTextSize", new Class<?>[]{float.class}, (float) size);
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        editorBox.addView(font);
        editorCard.addView(editorBox);
        settingsContainer.addView(editorCard, cardParams());

        MaterialCardView inputCard = card();
        LinearLayout inputBox = column(dp(14));
        inputBox.addView(heading("Keyboard & input"));
        inputBox.addView(body("Tap the code area or use this button whenever you have pasted code and want to edit it. The keyboard is deliberately available from every screen."));
        MaterialButton keyboard = actionButton("Show keyboard");
        keyboard.setOnClickListener(v -> showEditorKeyboard());
        inputBox.addView(keyboard);
        inputCard.addView(inputBox);
        settingsContainer.addView(inputCard, cardParams());

        MaterialCardView learningCard = card();
        LinearLayout learningBox = column(dp(14));
        learningBox.addView(heading("Learning help"));
        learningBox.addView(body("Every compile failure now opens PocketForge Fix Guide automatically. It explains the first error in plain English and tells you what to change. Full technical details remain in Console."));
        MaterialButton guide = actionButton("Open last compiler result");
        guide.setOnClickListener(v -> showPage(2));
        learningBox.addView(guide);
        learningCard.addView(learningBox);
        settingsContainer.addView(learningCard, cardParams());

        MaterialCardView appearanceCard = card();
        LinearLayout appearanceBox = column(dp(14));
        appearanceBox.addView(heading("Appearance"));
        appearanceBox.addView(body("Choose a complete palette and contrast mode. The editor, console, navigation and controls update together."));
        MaterialButton theme = actionButton("Choose theme");
        theme.setOnClickListener(v -> showThemePicker());
        appearanceBox.addView(theme);
        appearanceCard.addView(appearanceBox);
        settingsContainer.addView(appearanceCard, cardParams());
    }

    private void addTool(String title, String text, Runnable action) {
        MaterialCardView c = card(); LinearLayout box = column(dp(14)); box.addView(heading(title)); box.addView(body(text)); MaterialButton open = actionButton("Open"); open.setOnClickListener(v -> action.run()); box.addView(open); c.addView(box); toolsContainer.addView(c, cardParams());
    }

    private void showMoreMenu(View anchor) {
        PopupMenu p = new PopupMenu(this, anchor);
        String[] items = {"New file", "Import .java", "Save", "Show keyboard", "Settings", "Share code", "Rename file", "Delete file", "Undo", "Redo", "Find text", "Theme & appearance"};
        for (String item : items) p.getMenu().add(item);
        p.setOnMenuItemClickListener(item -> { switch (String.valueOf(item.getTitle())) {
            case "New file": promptNewFile(); break; case "Import .java": importJava(); break; case "Save": saveCurrent(); break; case "Show keyboard": showEditorKeyboard(); break; case "Settings": showPage(4); break; case "Share code": shareCode(); break;
            case "Rename file": promptRename(); break; case "Delete file": confirmDelete(); break; case "Undo": optional(editor, "undo", new Class<?>[0]); markDirty(); break;
            case "Redo": optional(editor, "redo", new Class<?>[0]); markDirty(); break; case "Find text": showFind(); break; case "Theme & appearance": showThemePicker(); break;
        } return true; }); p.show();
    }

    private void openFile(File file) {
        if (file == null) return; if (currentFile != null && dirty) saveCurrentQuietly();
        try { currentFile = file; loadingEditor = true; setEditorText(store.read(file)); dirty = false; updateSubtitle(); rebuildTabs(); }
        catch (Exception e) { toast("Could not open " + file.getName()); } finally { loadingEditor = false; }
    }

    private void rebuildTabs() {
        tabRow.removeAllViews(); for (File f : store.listJavaFiles()) { MaterialButton tab = smallButton((currentFile != null && f.equals(currentFile) ? "● " : "") + f.getName()); tab.setOnClickListener(v -> openFile(f)); tabRow.addView(tab); }
        MaterialButton plus = smallButton("+"); plus.setOnClickListener(v -> promptNewFile()); tabRow.addView(plus);
    }

    private void showFilesDialog() {
        List<File> files = store.listJavaFiles(); String[] names = new String[files.size()]; for (int i = 0; i < files.size(); i++) names[i] = files.get(i).getName();
        new MaterialAlertDialogBuilder(this).setTitle("Project files").setItems(names, (d, which) -> openFile(files.get(which))).setNegativeButton("Close", null).setNeutralButton("Import", (d, w) -> importJava()).setPositiveButton("New", (d, w) -> promptNewFile()).show();
    }

    private void promptNewFile() {
        EditText input = dialogInput("Example.java");
        new MaterialAlertDialogBuilder(this).setTitle("New Java file").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Create", (d, w) -> {
            String name = input.getText().toString().trim(); if (name.isEmpty()) return; File f = store.file(name); String cls = classNameFor(f.getName());
            try { store.write(f.getName(), "public class " + cls + " {\n    public static void main(String[] args) {\n        \n    }\n}\n"); openFile(f); } catch (Exception e) { toast("Could not create file"); }
        }).show();
    }

    private void promptRename() {
        if (currentFile == null) return; EditText input = dialogInput(currentFile.getName()); input.setText(currentFile.getName()); input.selectAll();
        new MaterialAlertDialogBuilder(this).setTitle("Rename file").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Rename", (d, w) -> {
            saveCurrentQuietly(); String name = input.getText().toString().trim(); if (name.isEmpty()) return; File target = store.file(name); if (target.exists()) { toast("A file with that name already exists"); return; }
            if (store.rename(currentFile, name)) { currentFile = target; dirty = false; updateSubtitle(); rebuildTabs(); } else toast("Rename failed");
        }).show();
    }

    private void confirmDelete() {
        if (currentFile == null) return; if (store.listJavaFiles().size() <= 1) { toast("Keep at least one Java file"); return; } File deleting = currentFile;
        new MaterialAlertDialogBuilder(this).setTitle("Delete " + deleting.getName() + "?").setMessage("This removes the file from this local project.").setNegativeButton("Cancel", null).setPositiveButton("Delete", (d, w) -> { if (store.delete(deleting)) { List<File> files = store.listJavaFiles(); if (!files.isEmpty()) openFile(files.get(0)); } }).show();
    }

    private void saveCurrent() { if (currentFile == null) return; try { store.write(currentFile.getName(), editorText()); dirty = false; updateSubtitle(); toast("Saved"); } catch (Exception e) { toast("Save failed: " + e.getMessage()); } }
    private void markDirty() { if (!loadingEditor) { dirty = true; updateSubtitle(); } }
    private void updateSubtitle() { subtitle.setText((currentFile == null ? "No file" : currentFile.getName()) + (dirty ? " • unsaved" : " • saved")); }
    private void formatActive() { setEditorText(JavaFormatter.format(editorText())); dirty = true; updateSubtitle(); toast("Formatted"); }

    private void runActiveSource() {
        if (running) { stopRunner(); return; }
        saveCurrentQuietly(); String source = editorText();
        if (DynamicJavaRunner.probablyNeedsInput(source)) {
            EditText stdin = new EditText(this); stdin.setHint("One answer per line"); stdin.setMinLines(4); stdin.setGravity(Gravity.TOP);
            new MaterialAlertDialogBuilder(this).setTitle("Program input").setMessage("Type what Scanner/System.in should receive.").setView(stdin).setNegativeButton("Cancel", null).setPositiveButton("Run", (d, w) -> runSource(source, stdin.getText().toString(), null)).show();
        } else runSource(source, "", null);
    }

    private interface RunCallback { void complete(boolean success, String output, long ms); }

    private void runSource(String source, String stdin, @Nullable RunCallback callback) {
        if (running) return; running = true; updateRunButtons(); appendConsole("\n> Running " + (currentFile == null ? "Java" : currentFile.getName()) + "\n"); consolePreview.setText("Compiling and running…");
        ArrayList<String> deps = new ArrayList<>(); for (File jar : maven.installedJars()) deps.add(jar.getAbsolutePath());
        ResultReceiver receiver = new ResultReceiver(new Handler(getMainLooper())) { @Override protected void onReceiveResult(int resultCode, Bundle resultData) {
            running = false; updateRunButtons(); String out = resultData == null ? "" : resultData.getString("output", ""); long ms = resultData == null ? 0 : resultData.getLong("durationMs", 0); boolean ok = resultCode == CodeRunnerService.RESULT_OK;
            if (resultCode == CodeRunnerService.RESULT_TIMEOUT) out += "\n[Stopped by safety watchdog]";
            appendConsole(out.isEmpty() ? "(no output)\n" : out + (out.endsWith("\n") ? "" : "\n")); appendConsole((ok ? "✓ Finished" : "✕ Failed") + (ms > 0 ? " in " + ms + " ms" : "") + "\n");
            if (!ok) showFixGuide(out);
            if (callback != null) callback.complete(ok, out, ms);
        }};
        Intent i = new Intent(this, CodeRunnerService.class); i.setAction(CodeRunnerService.ACTION_RUN); i.putExtra(CodeRunnerService.EXTRA_SOURCE, source); i.putExtra(CodeRunnerService.EXTRA_STDIN, stdin == null ? "" : stdin); i.putStringArrayListExtra(CodeRunnerService.EXTRA_DEPS, deps); i.putExtra(CodeRunnerService.EXTRA_RECEIVER, receiver); startService(i);
    }

    private void stopRunner() { Intent i = new Intent(this, CodeRunnerService.class); i.setAction(CodeRunnerService.ACTION_STOP); startService(i); running = false; updateRunButtons(); appendConsole("[Run stopped]\n"); }
    private void updateRunButtons() { btnRun.setText(running ? "Stop ■" : "Run ▶"); btnRunTop.setText(running ? "Stop ■" : "Run ▶"); }

    private void executeTerminal() {
        String command = terminalInput.getText().toString().trim(); terminalInput.setText(""); if (command.isEmpty()) return; appendConsole("$ " + command + "\n");
        String simple = command.toLowerCase(Locale.ROOT); if ("run".equals(simple)) { runActiveSource(); return; } if ("save".equals(simple)) { saveCurrent(); return; } if ("clear".equals(simple)) { consoleText.setText(""); consolePreview.setText("Console cleared"); return; }
        terminalInput.setEnabled(false); ioWorker.submit(() -> { String result = terminal.execute(command); runOnUiThread(() -> { terminalInput.setEnabled(true); if (!result.isEmpty()) appendConsole(result + "\n"); rebuildTabs(); terminalInput.requestFocus(); }); });
    }

    private void appendConsole(String s) { consoleText.append(s); String clean = s == null ? "" : s.trim(); if (!clean.isEmpty()) { String[] lines = clean.split("\n"); consolePreview.setText(lines[lines.length - 1]); } consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN)); }

    private void showFixGuide(String output) {
        String text = output == null || output.trim().isEmpty() ? "The run failed without an error message." : output;
        TextView guide = codeBlock(text);
        guide.setTextSize(12);
        new MaterialAlertDialogBuilder(this)
                .setTitle("PocketForge Fix Guide")
                .setMessage("Start with the first error. The guide below explains what to change; the technical compiler log is kept for reference.")
                .setView(wrapScroll(guide))
                .setNegativeButton("Close", null)
                .setNeutralButton("Open console", (d, w) -> showPage(2))
                .setPositiveButton("Back to code", (d, w) -> {
                    showPage(0);
                    showEditorKeyboard();
                })
                .show();
    }

    private void showExamples() {
        Map<String, String> examples = ExampleRepository.examples(); String[] names = examples.keySet().toArray(new String[0]);
        new MaterialAlertDialogBuilder(this).setTitle("PocketForge examples").setItems(names, (d, which) -> { String name = names[which], code = examples.get(name); new MaterialAlertDialogBuilder(this).setTitle(name).setView(wrapScroll(codeBlock(code))).setNegativeButton("Close", null).setNeutralButton("Copy", (x, w) -> copy(code)).setPositiveButton("Load", (x, w) -> { setEditorText(code); dirty = true; updateSubtitle(); showPage(0); }).show(); }).setNegativeButton("Close", null).show();
    }

    private void showRepl() {
        LinearLayout root = column(dp(12)); root.addView(body("Enter a Java statement such as int x = 5; or System.out.println(x * 2);")); TextView history = codeBlock(replBody.length() == 0 ? "REPL ready." : replBody.toString()); root.addView(history); EditText input = new EditText(this); input.setHint("System.out.println(2 + 3);"); input.setMinLines(2); input.setTypeface(Typeface.MONOSPACE); root.addView(input);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("Java REPL").setView(root).setNegativeButton("Close", null).setNeutralButton("Reset", null).setPositiveButton("Run", null).create();
        dialog.setOnShowListener(x -> { dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { replBody.setLength(0); history.setText("REPL reset."); }); dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String statement = input.getText().toString().trim(); if (statement.isEmpty()) return; String candidate = replBody + statement + "\n"; String wrapped = "public class ReplMain {\n    public static void main(String[] args) throws Exception {\n" + indent(candidate, 8) + "    }\n}\n"; history.setText("Running…");
            runSource(wrapped, "", (ok, output, ms) -> { if (ok) { replBody.append(statement).append('\n'); history.setText("> " + statement + "\n" + (output.isEmpty() ? "(no output)" : output)); input.setText(""); } else history.setText("Error:\n" + output); });
        }); }); dialog.show();
    }

    private void showMaven() {
        LinearLayout root = column(dp(12)); root.addView(body("Download from Maven Central. Example: org.apache.commons:commons-lang3:3.18.0")); EditText coordinate = dialogInput("group:artifact:version"); root.addView(coordinate); TextView status = codeBlock(maven.installedJars().size() + " JAR(s) installed"); root.addView(status);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("Maven libraries").setView(root).setNegativeButton("Close", null).setNeutralButton("Clear all", null).setPositiveButton("Add", null).create();
        dialog.setOnShowListener(x -> { dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { if (maven.clearAll()) status.setText("Libraries cleared."); }); dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { String c = coordinate.getText().toString().trim(); if (c.split(":").length != 3) { status.setText("Use group:artifact:version"); return; } status.setText("Resolving…"); ioWorker.submit(() -> { MavenResolver.ResolveResult r = maven.resolve(c); runOnUiThread(() -> status.setText(String.join("\n", r.messages) + "\nInstalled JARs: " + maven.installedJars().size())); }); }); }); dialog.show();
    }

    private void showEditorSettings() {
        LinearLayout root = column(dp(12)); SwitchMaterial wrap = new SwitchMaterial(this); wrap.setText("Word wrap"); wrap.setChecked(prefs.getBoolean("wrap", false)); root.addView(wrap); SwitchMaterial lines = new SwitchMaterial(this); lines.setText("Line numbers"); lines.setChecked(prefs.getBoolean("lines", true)); root.addView(lines);
        TextView fontLabel = body("Font size: " + prefs.getInt("font", 15) + " sp"); root.addView(fontLabel); SeekBar font = new SeekBar(this); font.setMax(12); font.setProgress(prefs.getInt("font", 15) - 12); root.addView(font); font.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean f) { fontLabel.setText("Font size: " + (12 + p) + " sp"); } public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {} });
        new MaterialAlertDialogBuilder(this).setTitle("Editor settings").setView(root).setNegativeButton("Cancel", null).setPositiveButton("Apply", (d, w) -> { int size = 12 + font.getProgress(); prefs.edit().putBoolean("wrap", wrap.isChecked()).putBoolean("lines", lines.isChecked()).putInt("font", size).apply(); optional(editor, "setWordwrap", new Class<?>[]{boolean.class}, wrap.isChecked()); optional(editor, "setLineNumberEnabled", new Class<?>[]{boolean.class}, lines.isChecked()); optional(editor, "setTextSize", new Class<?>[]{float.class}, (float) size); }).show();
    }

    private void showLanguageHub() {
        LinearLayout box = column(dp(14));
        box.addView(heading("Language hub"));
        box.addView(body("PocketForge is designed as a language-learning and coding workspace. Java is the first built-in compiler; other runtimes are represented as installable modules so the platform can grow without making the base APK unnecessarily large."));
        for (LanguageCatalog.Language language : LanguageCatalog.all()) {
            MaterialCardView card = card();
            LinearLayout row = column(dp(12));
            TextView title = heading(language.name);
            title.setTextSize(17);
            row.addView(title);
            row.addView(body(language.description));
            TextView status = body(language.statusLabel);
            status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            row.addView(status);
            card.addView(row);
            box.addView(card, cardParams());
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose your language path")
                .setView(wrapScroll(box))
                .setPositiveButton("Close", null)
                .show();
    }

    private void showDocs() {
        LinkedHashMap<String, String> docs = new LinkedHashMap<>(); docs.put("System.out.println", "Prints a value and starts a new line.\n\nSystem.out.println(\"Hello\");"); docs.put("String", "Stores text.\n\nString city = \"Delhi\";"); docs.put("int", "Whole number.\n\nint age = 25;"); docs.put("double", "Decimal number.\n\ndouble rate = 8.5;"); docs.put("boolean", "true or false.\n\nboolean ready = true;"); docs.put("if / else", "Choose between paths based on a condition."); docs.put("for loop", "Repeat code a known number of times."); docs.put("while loop", "Repeat while a condition remains true."); docs.put("Scanner", "Read input from System.in."); docs.put("ArrayList", "Resizable list."); docs.put("Math", "Math.max, Math.min, Math.sqrt and more."); docs.put("Method", "Reusable block of code."); docs.put("Class", "Blueprint for objects.");
        String[] names = docs.keySet().toArray(new String[0]); new MaterialAlertDialogBuilder(this).setTitle("Java quick docs").setItems(names, (d, which) -> new MaterialAlertDialogBuilder(this).setTitle(names[which]).setMessage(docs.get(names[which])).setPositiveButton("Close", null).show()).setNegativeButton("Close", null).show();
    }

    private void showCompatibility() {
        String text = "PocketForge 0.1 foundation\n\n"
                + "Available now\n"
                + "• Phone-first code editor with tabs and symbol keyboard\n"
                + "• On-device Java compile and run for learning projects\n"
                + "• Local files, examples, lessons, formatter and REPL\n"
                + "• Android-sandbox terminal commands\n"
                + "• Dedicated settings, themes and readable dark mode\n\n"
                + "Language platform roadmap\n"
                + "• Python, C/C++, JavaScript/Node.js, Kotlin, Go, Rust, PHP and shell modules\n"
                + "• Download-on-demand runtimes so the base APK stays practical\n"
                + "• Per-language editor, runner, package and learning metadata\n\n"
                + "The new platform branch is independent from PocketJava. No branch merge is performed unless explicitly requested.\n\n"
                + "Third-party: Sora Editor (LGPL-2.1), Eclipse Compiler for Java, Google R8/D8.";
        new MaterialAlertDialogBuilder(this).setTitle("Platform & licenses").setMessage(text).setPositiveButton("Close", null).show();
    }

    private void shareCode() { Intent send = new Intent(Intent.ACTION_SEND); send.setType("text/plain"); send.putExtra(Intent.EXTRA_SUBJECT, currentFile == null ? "Java code" : currentFile.getName()); send.putExtra(Intent.EXTRA_TEXT, editorText()); startActivity(Intent.createChooser(send, "Share Java code")); }
    private void importJava() { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/*"); startActivityForResult(i, REQ_IMPORT_JAVA); }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data); if (requestCode != REQ_IMPORT_JAVA || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return; importUri(data.getData());
    }

    private void importUri(Uri uri) {
        String name = queryName(uri); if (name == null || name.trim().isEmpty()) name = "Imported.java"; if (!name.toLowerCase(Locale.ROOT).endsWith(".java")) name += ".java";
        try (InputStream in = getContentResolver().openInputStream(uri); BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) { StringBuilder s = new StringBuilder(); String line; while ((line = br.readLine()) != null) s.append(line).append('\n'); File f = store.file(name); if (f.exists()) f = store.file(System.currentTimeMillis() + "_" + name); store.write(f.getName(), s.toString()); openFile(f); }
        catch (Exception e) { toast("Import failed: " + e.getMessage()); }
    }

    private String queryName(Uri uri) { try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) { if (c != null && c.moveToFirst()) return c.getString(0); } catch (Exception ignored) {} return null; }
    private void handleViewIntent(Intent intent) { if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) importUri(intent.getData()); }

    private void showFind() {
        EditText input = dialogInput("Search in current file"); new MaterialAlertDialogBuilder(this).setTitle("Find text").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Find", (d, w) -> { String q = input.getText().toString(), source = editorText(); int index = source.indexOf(q); if (q.isEmpty() || index < 0) { toast("Text not found"); return; } int line = 0, col = 0; for (int i = 0; i < index; i++) { if (source.charAt(i) == '\n') { line++; col = 0; } else col++; } if (!invokeBoolean(editor, "setSelection", new Class<?>[]{int.class, int.class}, line, col)) toast("Found at line " + (line + 1)); showPage(0); }).show();
    }

    private void applySavedThemeStyle() {
        String style = getSharedPreferences("ui", MODE_PRIVATE).getString(PREF_THEME_STYLE, "blue_system");
        if ("forest_dark".equals(style)) setTheme(R.style.Theme_JavaPocketLab_Forest);
        else if ("violet_dark".equals(style)) setTheme(R.style.Theme_JavaPocketLab_Violet);
        else setTheme(R.style.Theme_JavaPocketLab);
    }

    private void showThemePicker() {
        String[] labels = {
                "Classic blue — follow system",
                "Paper blue — light",
                "Midnight blue — dark",
                "Forest — dark",
                "Violet — dark"
        };
        String current = prefs.getString(PREF_THEME_STYLE, "blue_system");
        int selected = "blue_light".equals(current) ? 1
                : "blue_dark".equals(current) ? 2
                : "forest_dark".equals(current) ? 3
                : "violet_dark".equals(current) ? 4 : 0;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose theme")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    String style;
                    int mode;
                    switch (which) {
                        case 1: style = "blue_light"; mode = AppCompatDelegate.MODE_NIGHT_NO; break;
                        case 2: style = "blue_dark"; mode = AppCompatDelegate.MODE_NIGHT_YES; break;
                        case 3: style = "forest_dark"; mode = AppCompatDelegate.MODE_NIGHT_YES; break;
                        case 4: style = "violet_dark"; mode = AppCompatDelegate.MODE_NIGHT_YES; break;
                        default: style = "blue_system"; mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
                    }
                    prefs.edit().putString(PREF_THEME_STYLE, style).apply();
                    dialog.dismiss();
                    AppCompatDelegate.setDefaultNightMode(mode);
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void insertAtCursor(String text) {
        showEditorKeyboard();
        try { Object cursor = editor.getClass().getMethod("getCursor").invoke(editor); int line = ((Number) cursor.getClass().getMethod("getLeftLine").invoke(cursor)).intValue(); int col = ((Number) cursor.getClass().getMethod("getLeftColumn").invoke(cursor)).intValue(); Object content = editor.getText(); Method insert = content.getClass().getMethod("insert", int.class, int.class, CharSequence.class); insert.invoke(content, line, col, text); markDirty(); return; } catch (Throwable ignored) {}
        setEditorText(editorText() + text); markDirty();
    }

    private String editorText() { return editor.getText() == null ? "" : editor.getText().toString(); }
    private void setEditorText(String text) { loadingEditor = true; editor.setText(text == null ? "" : text); loadingEditor = false; }
    private void showEditorKeyboard() {
        if (pages.getDisplayedChild() != 0) showPage(0);
        editor.requestFocus();
        editor.postDelayed(() -> {
            InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        }, 120);
    }
    private void showPage(int index) { pages.setDisplayedChild(index); int[] ids = {R.id.navEditor, R.id.navLearn, R.id.navConsole, R.id.navTools}; for (int i = 0; i < ids.length; i++) { View v = findViewById(ids[i]); if (v instanceof MaterialButton) ((MaterialButton) v).setChecked(i == index); } }
    private void saveCurrentQuietly() { if (currentFile == null) return; try { store.write(currentFile.getName(), editorText()); dirty = false; updateSubtitle(); } catch (Exception ignored) {} }

    @Override protected void onPause() { saveCurrentQuietly(); super.onPause(); }
    @Override protected void onDestroy() { if (running) stopRunner(); ioWorker.shutdownNow(); try { editor.release(); } catch (Throwable ignored) {} super.onDestroy(); }

    private MaterialCardView card() {
        boolean dark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        MaterialCardView c = new MaterialCardView(this);
        c.setRadius(dp(18));
        c.setCardElevation(0);
        c.setCardBackgroundColor(dark ? 0xFF1C1D24 : 0xFFFFFFFF);
        c.setStrokeWidth(dp(1));
        c.setStrokeColor(dark ? 0xFF30313A : 0x16000000);
        return c;
    }
    private LinearLayout.LayoutParams cardParams() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.topMargin = dp(10); return p; }
    private LinearLayout column(int padding) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(padding, padding, padding, padding); return l; }
    private TextView heading(String text) { TextView t = new TextView(this); t.setText(text); t.setTextSize(20); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setPadding(0, 0, 0, dp(6)); return t; }
    private TextView label(String text) { TextView t = heading(text); t.setTextSize(14); t.setPadding(0, dp(10), 0, dp(4)); return t; }
    private TextView body(String text) { TextView t = new TextView(this); t.setText(text); t.setTextSize(14); t.setLineSpacing(0, 1.15f); t.setPadding(0, 0, 0, dp(8)); return t; }
    private TextView codeBlock(String text) { TextView t = new TextView(this); t.setText(text); t.setTextSize(13); t.setTypeface(Typeface.MONOSPACE); t.setTextIsSelectable(true); t.setTextColor(0xFFE8ECF6); t.setBackgroundColor(0xFF171B24); t.setPadding(dp(12), dp(10), dp(12), dp(10)); return t; }
    private MaterialButton actionButton(String text) { MaterialButton b = new MaterialButton(this); b.setText(text); b.setAllCaps(false); return b; }
    private MaterialButton smallButton(String text) { MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle); b.setText(text); b.setAllCaps(false); b.setTextSize(13); b.setMinHeight(dp(38)); b.setInsetTop(0); b.setInsetBottom(0); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)); p.setMarginEnd(dp(4)); b.setLayoutParams(p); return b; }
    private EditText dialogInput(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(true); int pad = dp(20); e.setPadding(pad, e.getPaddingTop(), pad, e.getPaddingBottom()); return e; }
    private ScrollView wrapScroll(View child) { ScrollView s = new ScrollView(this); s.addView(child); return s; }
    private void copy(String text) { ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); cb.setPrimaryClip(ClipData.newPlainText("Java code", text)); toast("Copied"); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private static String classNameFor(String fileName) { String s = fileName == null ? "Main" : fileName.replaceFirst("(?i)\\.java$", "").replaceAll("[^A-Za-z0-9_$]", "_"); if (s.isEmpty() || Character.isDigit(s.charAt(0))) s = "Main"; return s; }
    private static String indent(String s, int spaces) { String pad = " ".repeat(Math.max(0, spaces)); StringBuilder out = new StringBuilder(); for (String line : s.split("\\n", -1)) { if (!line.isEmpty()) out.append(pad).append(line); out.append('\n'); } return out.toString(); }
    private static void optional(Object target, String name, Class<?>[] types, Object... args) { try { target.getClass().getMethod(name, types).invoke(target, args); } catch (Throwable ignored) {} }
    private static boolean invokeBoolean(Object target, String name, Class<?>[] types, Object... args) { try { target.getClass().getMethod(name, types).invoke(target, args); return true; } catch (Throwable ignored) { return false; } }
}
