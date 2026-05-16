package com.mindmatrix.nammapustaka;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BRAND = Color.rgb(108, 47, 47);
    private static final int BRAND_DARK = Color.rgb(66, 29, 29);
    private static final int ACCENT = Color.rgb(47, 111, 105);
    private static final int PAPER = Color.rgb(255, 249, 237);
    private static final int INK = Color.rgb(34, 31, 28);
    private static final int OVERDUE = Color.rgb(188, 44, 44);
    private static final int REQ_CAMERA = 201;
    private static final int REQ_CAPTURE = 202;

    private LibraryDb db;
    private LinearLayout root;
    private LinearLayout content;
    private String activeTab = "Catalog";
    private String activeCategory = "All";
    private String searchText = "";
    private boolean capturedCover = false;
    private final ExecutorService network = Executors.newFixedThreadPool(4);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new LibraryDb(this);
        buildFrame();
        showCatalog();
    }

    private void buildFrame() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAPER);
        setContentView(root);
    }

    private void renderShell(String title) {
        root.removeAllViews();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(16), dp(18), dp(12));
        header.setBackgroundColor(PAPER);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView app = text("Namma-Pustaka", 26, BRAND_DARK, true);
        header.addView(app);

        TextView subtitle = text(title, 14, ACCENT, false);
        subtitle.setPadding(0, dp(2), 0, 0);
        header.addView(subtitle);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(10), dp(14), dp(18));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(6), dp(8), dp(8));
        nav.setBackgroundColor(Color.WHITE);
        root.addView(nav, new LinearLayout.LayoutParams(-1, -2));

        addNavButton(nav, "Catalog");
        addNavButton(nav, "Online");
        addNavButton(nav, "Scan");
        addNavButton(nav, "Add");
        addNavButton(nav, "Leaders");
    }

    private void addNavButton(LinearLayout nav, String label) {
        Button b = button(label);
        b.setTextColor(label.equals(activeTab) ? Color.WHITE : BRAND_DARK);
        b.setBackgroundColor(label.equals(activeTab) ? BRAND : Color.TRANSPARENT);
        b.setOnClickListener(v -> {
            activeTab = label;
            if ("Catalog".equals(label)) showCatalog();
            if ("Online".equals(label)) showOnlineSearch();
            if ("Scan".equals(label)) showScan();
            if ("Add".equals(label)) showAddBook();
            if ("Leaders".equals(label)) showLeaderboard();
        });
        nav.addView(b, new LinearLayout.LayoutParams(0, dp(46), 1));
    }

    private void showCatalog() {
        activeTab = "Catalog";
        renderShell("Digital shelf, search, borrowing, reviews");

        EditText search = input("Search by book name or author");
        search.setSingleLine(true);
        search.setText(searchText);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchText = s.toString();
                drawBookGrid();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        content.addView(search);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(10), 0, dp(8));
        content.addView(chips);
        for (String category : new String[]{"All", "Story", "Science", "History"}) {
            Button chip = button(category);
            chip.setTextColor(category.equals(activeCategory) ? Color.WHITE : BRAND_DARK);
            chip.setBackgroundColor(category.equals(activeCategory) ? ACCENT : Color.WHITE);
            chip.setOnClickListener(v -> {
                activeCategory = category;
                hideKeyboard(search);
                showCatalog();
            });
            chips.addView(chip, new LinearLayout.LayoutParams(0, dp(42), 1));
        }

        drawBookGrid();
    }

    private void drawBookGrid() {
        if (content.getChildCount() > 2) {
            content.removeViews(2, content.getChildCount() - 2);
        }

        List<Book> books = db.books(searchText, activeCategory);
        if (books.isEmpty()) {
            TextView empty = text("No books found. Add a new book from the Add tab.", 16, INK, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(50), dp(10), dp(50));
            content.addView(empty);
            return;
        }

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        content.addView(grid);

        for (Book book : books) {
            LinearLayout card = bookCard(book);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(5), dp(5), dp(5), dp(10));
            grid.addView(card, lp);
        }
    }

    private LinearLayout bookCard(Book book) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackgroundColor(Color.WHITE);
        card.setMinimumHeight(dp(220));
        card.setOnClickListener(v -> showBookDetail(book.id));

        ImageView cover = coverImage(book, false);
        card.addView(cover, new LinearLayout.LayoutParams(-1, dp(132)));

        TextView title = text(book.title, 16, INK, true);
        title.setPadding(0, dp(10), 0, 0);
        card.addView(title);

        card.addView(text(book.author, 13, Color.DKGRAY, false));
        card.addView(text(book.category + " | " + book.pages + " pages", 12, ACCENT, false));

        TextView status = text(book.statusText(), 12, book.isOverdue() ? OVERDUE : BRAND_DARK, true);
        status.setPadding(0, dp(6), 0, 0);
        card.addView(status);

        TextView code = text("Code: " + book.qrCode, 11, Color.GRAY, false);
        code.setPadding(0, dp(4), 0, 0);
        card.addView(code);
        return card;
    }

    private void showBookDetail(long id) {
        Book book = db.book(id);
        if (book == null) return;

        LinearLayout box = dialogBox();
        ImageView cover = coverImage(book, true);
        box.addView(cover, new LinearLayout.LayoutParams(-1, dp(180)));
        TextView code = text("Book code: " + book.qrCode, 13, ACCENT, true);
        code.setGravity(Gravity.CENTER);
        code.setPadding(0, dp(8), 0, dp(4));
        box.addView(code);
        box.addView(text(book.title, 22, BRAND_DARK, true));
        box.addView(text("By " + book.author + " | " + book.category, 14, ACCENT, false));
        box.addView(gap(8));
        box.addView(text("Kannada summary", 14, INK, true));
        box.addView(text(book.summary, 15, INK, false));
        box.addView(gap(8));
        box.addView(text(book.statusText(), 15, book.isOverdue() ? OVERDUE : BRAND_DARK, true));
        box.addView(text("Rating: " + stars(book.rating), 15, INK, false));
        box.addView(text("Review: " + (book.review.isEmpty() ? "No review yet" : book.review), 14, Color.DKGRAY, false));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).create();

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, 0);
        box.addView(actions);

        Button issue = button(book.available ? "Reserve" : "Return");
        issue.setOnClickListener(v -> {
            dialog.dismiss();
            if (book.available) showIssueDialog(book);
            else {
                db.returnBook(book.id);
                Toast.makeText(this, "Book returned", Toast.LENGTH_SHORT).show();
                showCatalog();
            }
        });
        actions.addView(issue, new LinearLayout.LayoutParams(0, dp(46), 1));

        Button review = button("Review");
        review.setOnClickListener(v -> {
            dialog.dismiss();
            showReviewDialog(book);
        });
        actions.addView(review, new LinearLayout.LayoutParams(0, dp(46), 1));

        dialog.show();
    }

    private void showIssueDialog(Book book) {
        LinearLayout box = dialogBox();
        box.addView(text("Reserve " + book.title, 20, BRAND_DARK, true));
        EditText name = input("Student name");
        EditText days = input("Days allowed, example 7");
        days.setText("7");
        box.addView(name);
        box.addView(days);

        new AlertDialog.Builder(this)
                .setView(box)
                .setPositiveButton("Reserve", (d, which) -> {
                    String student = name.getText().toString().trim();
                    int dueDays = parseInt(days.getText().toString(), 7);
                    if (student.isEmpty()) student = "Student";
                    db.issueBook(book.id, student, dueDays, book.pages);
                    Toast.makeText(this, "Reserved for " + student, Toast.LENGTH_SHORT).show();
                    showCatalog();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showReviewDialog(Book book) {
        LinearLayout box = dialogBox();
        box.addView(text("Review Corner", 20, BRAND_DARK, true));
        EditText rating = input("Star rating 1 to 5");
        rating.setText(String.valueOf(Math.max(1, book.rating)));
        EditText review = input("One sentence review");
        review.setText(book.review);
        box.addView(rating);
        box.addView(review);

        new AlertDialog.Builder(this)
                .setView(box)
                .setPositiveButton("Save", (d, which) -> {
                    db.reviewBook(book.id, parseInt(rating.getText().toString(), 5), review.getText().toString().trim());
                    Toast.makeText(this, "Review saved", Toast.LENGTH_SHORT).show();
                    showCatalog();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showScan() {
        activeTab = "Scan";
        renderShell("QR borrow desk");
        content.addView(text("QR Borrow", 22, BRAND_DARK, true));
        content.addView(text("Enter the code printed on a book card to issue or return it.", 15, INK, false));
        content.addView(gap(10));

        EditText code = input("Book code, example NP-101");
        EditText student = input("Student name for issue");
        content.addView(code);
        content.addView(student);

        Button issue = button("Issue / Reserve");
        issue.setOnClickListener(v -> {
            Book book = db.bookByCode(code.getText().toString().trim());
            if (book == null) {
                toast("Book code not found");
                return;
            }
            if (!book.available) {
                toast("Already borrowed by " + book.borrower);
                return;
            }
            String name = student.getText().toString().trim();
            if (name.isEmpty()) name = "Student";
            db.issueBook(book.id, name, 7, book.pages);
            toast("Issued " + book.title + " to " + name);
            showScan();
        });
        content.addView(issue, new LinearLayout.LayoutParams(-1, dp(50)));

        Button returns = button("Return Book");
        returns.setOnClickListener(v -> {
            Book book = db.bookByCode(code.getText().toString().trim());
            if (book == null) {
                toast("Book code not found");
                return;
            }
            db.returnBook(book.id);
            toast("Returned " + book.title);
            showScan();
        });
        content.addView(returns, new LinearLayout.LayoutParams(-1, dp(50)));

        content.addView(gap(16));
        content.addView(text("Teacher hint: each book card shows its code. Schools can write this code below a printed QR label.", 14, Color.DKGRAY, false));
    }

    private void showOnlineSearch() {
        activeTab = "Online";
        renderShell("Search books from Open Library");
        content.addView(text("Online Book Search", 22, BRAND_DARK, true));
        content.addView(text("Search by title, author, subject, or ISBN. Save useful books into your school catalog.", 15, INK, false));
        content.addView(text("Needs phone internet. If search fails, connect Wi-Fi/mobile data and try again.", 13, Color.DKGRAY, false));
        content.addView(gap(10));

        EditText query = input("Example: kannada stories, science, Abdul Kalam");
        query.setSingleLine(true);
        content.addView(query);

        Button search = button("Search Books");
        content.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, dp(12), 0, 0);
        content.addView(results);
        results.addView(text("Popular searches: Harry Potter, Panchatantra, Kannada stories, APJ Abdul Kalam, science experiments, solar system.", 14, Color.DKGRAY, false));

        search.setOnClickListener(v -> {
            String q = query.getText().toString().trim();
            if (q.isEmpty()) {
                toast("Type a book name or author");
                return;
            }
            hideKeyboard(query);
            results.removeAllViews();
            results.addView(text("Searching online...", 16, ACCENT, true));
            fetchOnlineBooks(q, results);
        });
    }

    private void fetchOnlineBooks(String query, LinearLayout results) {
        network.execute(() -> {
            ArrayList<OnlineBook> found = new ArrayList<>();
            String error = "";
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String api = "https://openlibrary.org/search.json?q=" + encoded + "&fields=title,author_name,first_publish_year,cover_i,isbn,subject&limit=12";
                JSONObject root = new JSONObject(readUrl(api));
                JSONArray docs = root.optJSONArray("docs");
                if (docs != null) {
                    for (int i = 0; i < docs.length(); i++) {
                        JSONObject item = docs.getJSONObject(i);
                        OnlineBook book = new OnlineBook();
                        book.title = item.optString("title", "Untitled");
                        JSONArray authors = item.optJSONArray("author_name");
                        book.author = authors != null && authors.length() > 0 ? authors.optString(0, "Unknown") : "Unknown";
                        book.year = item.optInt("first_publish_year", 0);
                        int coverId = item.optInt("cover_i", 0);
                        if (coverId > 0) {
                            book.coverUrl = "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg";
                        }
                        JSONArray isbn = item.optJSONArray("isbn");
                        book.code = isbn != null && isbn.length() > 0 ? isbn.optString(0, "") : "";
                        JSONArray subjects = item.optJSONArray("subject");
                        book.category = chooseCategory(subjects);
                        found.add(book);
                    }
                }
            } catch (UnknownHostException e) {
                error = "No internet or DNS on this phone. Connect Wi-Fi/mobile data, then search again.";
            } catch (Exception e) {
                error = e.getMessage() == null ? "Network error" : e.getMessage();
            }

            String finalError = error;
            runOnUiThread(() -> drawOnlineResults(results, found, finalError));
        });
    }

    private void drawOnlineResults(LinearLayout results, List<OnlineBook> books, String error) {
        results.removeAllViews();
        if (!error.isEmpty()) {
            results.addView(text("Could not search online: " + error, 15, OVERDUE, true));
            results.addView(gap(8));
            results.addView(text("The local catalog still works offline. Online book covers need access to openlibrary.org and covers.openlibrary.org.", 14, Color.DKGRAY, false));
            return;
        }
        if (books.isEmpty()) {
            results.addView(text("No online books found. Try another title or author.", 15, INK, false));
            return;
        }
        for (OnlineBook book : books) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(10), dp(10), dp(10), dp(10));
            row.setBackgroundColor(Color.WHITE);

            ImageView cover = new ImageView(this);
            cover.setBackgroundColor(BRAND);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setImageResource(fallbackCoverForCategory(book.category));
            loadRemoteCover(book.coverUrl, cover);
            row.addView(cover, new LinearLayout.LayoutParams(dp(88), dp(120)));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, 0, 0);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

            info.addView(text(book.title, 17, BRAND_DARK, true));
            info.addView(text(book.author + (book.year > 0 ? " | " + book.year : ""), 13, ACCENT, false));
            info.addView(text(book.category, 13, Color.DKGRAY, false));

            Button save = button("Save to Catalog");
            save.setOnClickListener(v -> {
                Book local = new Book();
                local.title = book.title;
                local.author = book.author;
                local.category = book.category;
                local.pages = 100;
                local.summary = "Online book added from Open Library. Teacher can update the Kannada summary later.";
                local.qrCode = book.code.isEmpty() ? "NP-" + (100 + db.countBooks() + 1) : "ISBN-" + book.code;
                local.coverColor = ACCENT;
                local.coverUrl = book.coverUrl;
                db.insertBook(local);
                toast("Saved " + book.title);
            });
            info.addView(save, new LinearLayout.LayoutParams(-1, dp(44)));

            results.addView(row, new LinearLayout.LayoutParams(-1, -2));
            results.addView(gap(10));
        }
    }

    private void showAddBook() {
        activeTab = "Add";
        renderShell("Camera-based book entry");
        capturedCover = false;
        drawAddForm();
    }

    private void drawAddForm() {
        content.removeAllViews();
        content.addView(text("Add New Book", 22, BRAND_DARK, true));
        content.addView(text("Capture the book cover, then enter book details for the digital shelf.", 15, INK, false));
        content.addView(gap(8));

        TextView cameraStatus = text(capturedCover ? "Cover photo captured" : "No cover photo captured yet", 15, capturedCover ? ACCENT : OVERDUE, true);
        content.addView(cameraStatus);

        Button camera = button("Open Camera");
        camera.setOnClickListener(v -> openCamera());
        content.addView(camera, new LinearLayout.LayoutParams(-1, dp(50)));

        EditText title = input("Book name");
        EditText author = input("Author");
        EditText category = input("Category: Story, Science, History");
        EditText pages = input("Pages");
        EditText summary = input("Kannada summary");
        content.addView(title);
        content.addView(author);
        content.addView(category);
        content.addView(pages);
        content.addView(summary);

        Button save = button("Save Book");
        save.setOnClickListener(v -> {
            if (title.getText().toString().trim().isEmpty()) {
                toast("Book name is required");
                return;
            }
            Book book = new Book();
            book.title = title.getText().toString().trim();
            book.author = author.getText().toString().trim().isEmpty() ? "Unknown" : author.getText().toString().trim();
            book.category = normalizeCategory(category.getText().toString().trim());
            book.pages = parseInt(pages.getText().toString(), 80);
            book.summary = summary.getText().toString().trim().isEmpty() ? "ಈ ಪುಸ್ತಕ ವಿದ್ಯಾರ್ಥಿಗಳಿಗೆ ಉಪಯುಕ್ತವಾದ ಸರಳ ಓದು." : summary.getText().toString().trim();
            book.qrCode = "NP-" + (100 + db.countBooks() + 1);
            book.coverColor = capturedCover ? ACCENT : BRAND;
            book.coverUrl = "";
            db.insertBook(book);
            toast("Book added to catalog");
            activeCategory = "All";
            searchText = "";
            showCatalog();
        });
        content.addView(save, new LinearLayout.LayoutParams(-1, dp(54)));
    }

    private void showLeaderboard() {
        activeTab = "Leaders";
        renderShell("Monthly reading leaderboard");
        content.addView(text("Reading Leaderboard", 22, BRAND_DARK, true));
        content.addView(text("Tracks student reading pages from issued books this month.", 15, INK, false));
        content.addView(gap(12));

        List<Leader> leaders = db.leaderboard();
        if (leaders.isEmpty()) {
            content.addView(text("No reading activity yet. Issue a book to start the leaderboard.", 16, INK, false));
            return;
        }

        int rank = 1;
        for (Leader leader : leaders) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setBackgroundColor(Color.WHITE);
            TextView name = text(rank + ". " + leader.name, 18, BRAND_DARK, true);
            TextView pages = text(leader.pages + " pages", 16, ACCENT, true);
            pages.setGravity(Gravity.RIGHT);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(pages, new LinearLayout.LayoutParams(0, -2, 1));
            content.addView(row, new LinearLayout.LayoutParams(-1, -2));
            content.addView(gap(8));
            rank++;
        }
    }

    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQ_CAPTURE);
        } else {
            capturedCover = true;
            toast("Camera app not found. Marked as captured for demo.");
            drawAddForm();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE && resultCode == RESULT_OK) {
            capturedCover = true;
            toast("Cover photo captured");
            drawAddForm();
        }
    }

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(12));
        return box;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setLineSpacing(dp(2), 1f);
        if (bold) tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return tv;
    }

    private EditText input(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextColor(INK);
        edit.setHintTextColor(Color.GRAY);
        edit.setSingleLine(false);
        edit.setPadding(dp(12), dp(8), dp(12), dp(8));
        return edit;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(BRAND);
        return button;
    }

    private ImageView coverImage(Book book, boolean detail) {
        ImageView image = new ImageView(this);
        image.setImageResource(coverResFor(book));
        image.setBackgroundColor(book.coverColor);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setAdjustViewBounds(false);
        image.setContentDescription(book.title + " cover");
        image.setPadding(detail ? dp(22) : dp(8), detail ? dp(8) : dp(4), detail ? dp(22) : dp(8), detail ? dp(8) : dp(4));
        loadRemoteCover(book.coverUrl, image);
        return image;
    }

    private int coverResFor(Book book) {
        if ("NP-101".equals(book.qrCode)) return R.drawable.cover_malgudi_days;
        if ("NP-102".equals(book.qrCode)) return R.drawable.cover_wings_of_fire;
        if ("NP-103".equals(book.qrCode)) return R.drawable.cover_fun_with_science;
        if ("NP-104".equals(book.qrCode)) return R.drawable.cover_tenali_rama;
        if ("NP-105".equals(book.qrCode)) return R.drawable.cover_freedom_story;
        if ("NP-106".equals(book.qrCode)) return R.drawable.cover_solar_system;
        if ("Science".equalsIgnoreCase(book.category)) return R.drawable.cover_fun_with_science;
        if ("History".equalsIgnoreCase(book.category)) return R.drawable.cover_freedom_story;
        return R.drawable.cover_tenali_rama;
    }

    private int fallbackCoverForCategory(String category) {
        if ("Science".equalsIgnoreCase(category)) return R.drawable.cover_fun_with_science;
        if ("History".equalsIgnoreCase(category)) return R.drawable.cover_freedom_story;
        return R.drawable.cover_tenali_rama;
    }

    private void loadRemoteCover(String coverUrl, ImageView image) {
        if (coverUrl == null || coverUrl.trim().isEmpty()) return;
        image.setPadding(0, 0, 0, 0);
        network.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(coverUrl).openConnection();
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(7000);
                conn.connect();
                InputStream stream = conn.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                stream.close();
                conn.disconnect();
                if (bitmap != null) {
                    runOnUiThread(() -> image.setImageBitmap(bitmap));
                }
            } catch (Exception ignored) {
                // Keep the local fallback cover if the network image is unavailable.
            }
        });
    }

    private String readUrl(String spec) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(spec).openConnection();
        conn.setConnectTimeout(9000);
        conn.setReadTimeout(9000);
        conn.setRequestProperty("User-Agent", "Namma-Pustaka Android student project");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new Exception("Open Library returned HTTP " + code);
        }
        InputStream stream = conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) out.append(line);
        reader.close();
        conn.disconnect();
        return out.toString();
    }

    private String chooseCategory(JSONArray subjects) {
        String joined = subjects == null ? "" : subjects.toString().toLowerCase(Locale.US);
        if (joined.contains("science") || joined.contains("technology") || joined.contains("mathematics")) return "Science";
        if (joined.contains("history") || joined.contains("biography") || joined.contains("india")) return "History";
        return "Story";
    }

    private Space gap(int dp) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
        return space;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private String normalizeCategory(String value) {
        if (value == null || value.trim().isEmpty()) return "Story";
        String clean = value.trim();
        return clean.substring(0, 1).toUpperCase(Locale.US) + clean.substring(1).toLowerCase(Locale.US);
    }

    private String stars(int rating) {
        int safe = Math.max(0, Math.min(5, rating));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < safe; i++) out.append("*");
        if (safe == 0) out.append("No rating");
        return out.toString();
    }

    private static class Book {
        long id;
        String title = "";
        String author = "";
        String category = "";
        int pages;
        String summary = "";
        String qrCode = "";
        int coverColor = BRAND;
        String coverUrl = "";
        boolean available = true;
        String borrower = "";
        long dueAt = 0;
        int rating = 0;
        String review = "";

        boolean isOverdue() {
            return !available && dueAt > 0 && System.currentTimeMillis() > dueAt;
        }

        String statusText() {
            if (available) return "Available";
            String due = new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date(dueAt));
            return (isOverdue() ? "Overdue" : "Borrowed") + " by " + borrower + " | Due " + due;
        }
    }

    private static class Leader {
        String name;
        int pages;
    }

    private static class OnlineBook {
        String title = "";
        String author = "";
        String category = "Story";
        String coverUrl = "";
        String code = "";
        int year;
    }

    private static class LibraryDb extends SQLiteOpenHelper {
        LibraryDb(Context context) {
            super(context, "namma_pustaka.db", null, 2);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE books (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "title TEXT, author TEXT, category TEXT, pages INTEGER, summary TEXT, qr_code TEXT UNIQUE," +
                    "cover_color INTEGER, cover_url TEXT, available INTEGER, borrower TEXT, due_at INTEGER, rating INTEGER, review TEXT)");
            db.execSQL("CREATE TABLE reads (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "student TEXT, pages INTEGER, created_at INTEGER)");
            seed(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE books ADD COLUMN cover_url TEXT DEFAULT ''");
            }
        }

        private void seed(SQLiteDatabase db) {
            insertSeed(db, "Malgudi Days", "R. K. Narayan", "Story", 180, "ಮಾಲ್ಗುಡಿ ಊರಿನ ಸರಳ ಕಥೆಗಳು ಮಕ್ಕಳಿಗೆ ಓದಿನ ಆಸಕ್ತಿ ಬೆಳೆಸುತ್ತವೆ.", "NP-101", Color.rgb(108, 47, 47), 4, "Easy and fun stories.");
            insertSeed(db, "Wings of Fire", "A. P. J. Abdul Kalam", "History", 240, "ಕಲಾಂ ಅವರ ಜೀವನ ಕಥೆ ಪರಿಶ್ರಮ ಮತ್ತು ಕನಸುಗಳ ಮಹತ್ವ ತಿಳಿಸುತ್ತದೆ.", "NP-102", Color.rgb(47, 111, 105), 5, "Very inspiring.");
            insertSeed(db, "Fun With Science", "Meera Rao", "Science", 96, "ದೈನಂದಿನ ವಸ್ತುಗಳಿಂದ ವಿಜ್ಞಾನ ಕಲಿಯುವ ಸರಳ ಪ್ರಯೋಗಗಳ ಪುಸ್ತಕ.", "NP-103", Color.rgb(76, 86, 116), 4, "Good experiments.");
            insertSeed(db, "Tenali Rama Tales", "Anant Pai", "Story", 120, "ತೆನಾಲಿರಾಮನ ಬುದ್ಧಿವಂತ ಕಥೆಗಳು ಹಾಸ್ಯ ಮತ್ತು ನೀತಿಯನ್ನು ಕಲಿಸುತ್ತವೆ.", "NP-104", Color.rgb(169, 91, 55), 3, "");
            insertSeed(db, "Our Freedom Story", "School Board", "History", 140, "ಸ್ವಾತಂತ್ರ್ಯ ಹೋರಾಟದ ಪ್ರಮುಖ ಘಟನೆಗಳನ್ನು ಮಕ್ಕಳಿಗೆ ಸರಳವಾಗಿ ವಿವರಿಸುತ್ತದೆ.", "NP-105", Color.rgb(87, 104, 68), 0, "");
            insertSeed(db, "The Solar System", "Kavya S.", "Science", 88, "ಗ್ರಹಗಳು, ಚಂದ್ರ, ಸೂರ್ಯ ಮತ್ತು ಬಾಹ್ಯಾಕಾಶದ ಪರಿಚಯ ನೀಡುತ್ತದೆ.", "NP-106", Color.rgb(95, 68, 125), 5, "Nice pictures.");
        }

        private void insertSeed(SQLiteDatabase db, String title, String author, String category, int pages, String summary, String code, int color, int rating, String review) {
            ContentValues values = new ContentValues();
            values.put("title", title);
            values.put("author", author);
            values.put("category", category);
            values.put("pages", pages);
            values.put("summary", summary);
            values.put("qr_code", code);
            values.put("cover_color", color);
            values.put("cover_url", "");
            values.put("available", 1);
            values.put("borrower", "");
            values.put("due_at", 0);
            values.put("rating", rating);
            values.put("review", review);
            db.insert("books", null, values);
        }

        List<Book> books(String search, String category) {
            SQLiteDatabase r = getReadableDatabase();
            ArrayList<Book> books = new ArrayList<>();
            String like = "%" + (search == null ? "" : search) + "%";
            boolean all = category == null || "All".equals(category);
            String sql = all
                    ? "SELECT * FROM books WHERE title LIKE ? OR author LIKE ? ORDER BY title"
                    : "SELECT * FROM books WHERE (title LIKE ? OR author LIKE ?) AND category = ? ORDER BY title";
            String[] args = all ? new String[]{like, like} : new String[]{like, like, category};
            Cursor c = r.rawQuery(sql, args);
            while (c.moveToNext()) books.add(readBook(c));
            c.close();
            return books;
        }

        Book book(long id) {
            Cursor c = getReadableDatabase().rawQuery("SELECT * FROM books WHERE id = ?", new String[]{String.valueOf(id)});
            Book book = c.moveToFirst() ? readBook(c) : null;
            c.close();
            return book;
        }

        Book bookByCode(String code) {
            Cursor c = getReadableDatabase().rawQuery("SELECT * FROM books WHERE qr_code = ?", new String[]{code});
            Book book = c.moveToFirst() ? readBook(c) : null;
            c.close();
            return book;
        }

        long insertBook(Book book) {
            ContentValues values = new ContentValues();
            values.put("title", book.title);
            values.put("author", book.author);
            values.put("category", book.category);
            values.put("pages", book.pages);
            values.put("summary", book.summary);
            values.put("qr_code", book.qrCode);
            values.put("cover_color", book.coverColor);
            values.put("cover_url", book.coverUrl == null ? "" : book.coverUrl);
            values.put("available", 1);
            values.put("borrower", "");
            values.put("due_at", 0);
            values.put("rating", 0);
            values.put("review", "");
            return getWritableDatabase().insert("books", null, values);
        }

        void issueBook(long id, String student, int dueDays, int pages) {
            long now = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            values.put("available", 0);
            values.put("borrower", student);
            values.put("due_at", now + dueDays * 24L * 60L * 60L * 1000L);
            getWritableDatabase().update("books", values, "id = ?", new String[]{String.valueOf(id)});

            ContentValues read = new ContentValues();
            read.put("student", student);
            read.put("pages", pages);
            read.put("created_at", now);
            getWritableDatabase().insert("reads", null, read);
        }

        void returnBook(long id) {
            ContentValues values = new ContentValues();
            values.put("available", 1);
            values.put("borrower", "");
            values.put("due_at", 0);
            getWritableDatabase().update("books", values, "id = ?", new String[]{String.valueOf(id)});
        }

        void reviewBook(long id, int rating, String review) {
            ContentValues values = new ContentValues();
            values.put("rating", Math.max(1, Math.min(5, rating)));
            values.put("review", review);
            getWritableDatabase().update("books", values, "id = ?", new String[]{String.valueOf(id)});
        }

        int countBooks() {
            Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM books", null);
            int count = c.moveToFirst() ? c.getInt(0) : 0;
            c.close();
            return count;
        }

        List<Leader> leaderboard() {
            ArrayList<Leader> leaders = new ArrayList<>();
            long monthAgo = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L;
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT student, SUM(pages) AS pages FROM reads WHERE created_at >= ? GROUP BY student ORDER BY pages DESC LIMIT 10",
                    new String[]{String.valueOf(monthAgo)});
            while (c.moveToNext()) {
                Leader leader = new Leader();
                leader.name = c.getString(0);
                leader.pages = c.getInt(1);
                leaders.add(leader);
            }
            c.close();
            return leaders;
        }

        private Book readBook(Cursor c) {
            Book book = new Book();
            book.id = c.getLong(c.getColumnIndexOrThrow("id"));
            book.title = c.getString(c.getColumnIndexOrThrow("title"));
            book.author = c.getString(c.getColumnIndexOrThrow("author"));
            book.category = c.getString(c.getColumnIndexOrThrow("category"));
            book.pages = c.getInt(c.getColumnIndexOrThrow("pages"));
            book.summary = c.getString(c.getColumnIndexOrThrow("summary"));
            book.qrCode = c.getString(c.getColumnIndexOrThrow("qr_code"));
            book.coverColor = c.getInt(c.getColumnIndexOrThrow("cover_color"));
            int coverUrlIndex = c.getColumnIndex("cover_url");
            book.coverUrl = coverUrlIndex >= 0 ? c.getString(coverUrlIndex) : "";
            book.available = c.getInt(c.getColumnIndexOrThrow("available")) == 1;
            book.borrower = c.getString(c.getColumnIndexOrThrow("borrower"));
            book.dueAt = c.getLong(c.getColumnIndexOrThrow("due_at"));
            book.rating = c.getInt(c.getColumnIndexOrThrow("rating"));
            book.review = c.getString(c.getColumnIndexOrThrow("review"));
            return book;
        }
    }
}
