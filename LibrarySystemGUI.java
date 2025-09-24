import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;

// ----------------- Data Models -----------------
class User {
    String id, name, role;
    int fine;

    User(String id, String name, String role, int fine) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.fine = fine;
    }

    public Object[] toTableRow() {
        return new Object[]{id, name, role, "Rs" + fine};
    }

    public String toCSV() {
        return id + "," + name + "," + role + "," + fine;
    }
}

class Book {
    String id, title, author;
    boolean isBorrowed;
    String borrowedBy; // user ID
    LocalDate dueDate;
    java.util.List<String> waitlist;

    Book(String id, String title, String author, boolean isBorrowed, String borrowedBy, LocalDate dueDate, java.util.List<String> waitlist) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.isBorrowed = isBorrowed;
        this.borrowedBy = borrowedBy;
        this.dueDate = dueDate;
        this.waitlist = waitlist != null ? waitlist : new ArrayList<>();
    }

    public Object[] toTableRow() {
        String status = isBorrowed ? "BORROWED by " + borrowedBy + " due " + dueDate : "AVAILABLE";
        String waitlistStr = String.join(", ", waitlist);
        return new Object[]{id, title, author, status, waitlistStr};
    }

    public String toCSV() {
        String wl = String.join(";", waitlist);
        return id + "," + title + "," + author + "," + isBorrowed + "," + (borrowedBy == null ? "" : borrowedBy) + "," + (dueDate == null ? "" : dueDate) + "," + wl;
    }
}

// ----------------- Library Management -----------------
class LibraryDB {
    java.util.List<User> users = new ArrayList<>();
    java.util.List<Book> books = new ArrayList<>();
    private final String usersFile = "users.csv";
    private final String booksFile = "books.csv";

    public LibraryDB() {
        loadUsers();
        loadBooks();
    }

    // Load CSV files
    private void loadUsers() {
        users.clear();
        File file = new File(usersFile);
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    users.add(new User(parts[0], parts[1], parts[2], Integer.parseInt(parts[3])));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadBooks() {
        books.clear();
        File file = new File(booksFile);
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", -1);
                boolean borrowed = Boolean.parseBoolean(parts[3]);
                LocalDate due = parts[5].isEmpty() ? null : LocalDate.parse(parts[5]);
                java.util.List<String> wl = new ArrayList<>();
                if (parts.length >= 7 && !parts[6].isEmpty()) {
                    wl.addAll(Arrays.asList(parts[6].split(";")));
                }
                books.add(new Book(parts[0], parts[1], parts[2], borrowed, parts[4].isEmpty() ? null : parts[4], due, wl));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Save CSV files
    private void saveUsers() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(usersFile))) {
            for (User u : users) pw.println(u.toCSV());
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void saveBooks() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(booksFile))) {
            for (Book b : books) pw.println(b.toCSV());
        } catch (IOException e) { e.printStackTrace(); }
    }

    // Operations
    public void addUser(String id, String name, String role) {
        users.add(new User(id, name, role, 0));
        saveUsers();
    }

    public void addBook(String id, String title, String author) {
        books.add(new Book(id, title, author, false, null, null, new ArrayList<>()));
        saveBooks();
    }

    public String borrowBook(String userId, String bookId) {
        User user = users.stream().filter(u -> u.id.equals(userId)).findFirst().orElse(null);
        Book book = books.stream().filter(b -> b.id.equals(bookId)).findFirst().orElse(null);
        if (user == null) return "User not found!";
        if (book == null) return "Book not found!";

        if (book.isBorrowed) {
            if (!book.waitlist.contains(userId)) book.waitlist.add(userId);
            saveBooks();
            return "Book is already borrowed. Added to waitlist.";
        } else {
            book.isBorrowed = true;
            book.borrowedBy = userId;
            book.dueDate = LocalDate.now().plusDays(7);
            saveBooks();
            return "Book borrowed successfully until " + book.dueDate;
        }
    }

    public String returnBook(String userId, String bookId) {
        Book book = books.stream().filter(b -> b.id.equals(bookId)).findFirst().orElse(null);
        if (book == null) return "Book not found!";
        if (!userId.equals(book.borrowedBy)) return "This book was not borrowed by this user.";

        StringBuilder msg = new StringBuilder();
        User user = users.stream().filter(u -> u.id.equals(userId)).findFirst().orElse(null);

        if (book.dueDate != null && LocalDate.now().isAfter(book.dueDate)) {
            long daysLate = LocalDate.now().toEpochDay() - book.dueDate.toEpochDay();
            int fine = (int) daysLate * 2;
            if (user != null) user.fine += fine;
            msg.append("Overdue! Fine Rs").append(fine).append("\n");
        }

        book.isBorrowed = false;
        book.borrowedBy = null;
        book.dueDate = null;

        if (!book.waitlist.isEmpty()) {
            String nextUserId = book.waitlist.remove(0);
            book.isBorrowed = true;
            book.borrowedBy = nextUserId;
            book.dueDate = LocalDate.now().plusDays(7);
            msg.append("Book auto-assigned to waitlisted user: ").append(nextUserId).append("\n");
        }

        saveBooks();
        saveUsers();
        msg.append("Book returned successfully.");
        return msg.toString();
    }

    public java.util.List<User> getUsers() {
        return users;
    }

    public java.util.List<Book> getBooks() {
        return books;
    }
}

// ----------------- GUI -----------------
public class LibrarySystemGUI extends JFrame {
    private LibraryDB lib = new LibraryDB();

    public LibrarySystemGUI() {
        setTitle("Library Management System");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(245, 245, 245));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 14));
        tabs.setForeground(Color.BLUE);

        // --- Add User ---
        JPanel addUserPanel = new JPanel(new GridLayout(5, 2, 10, 10));
        addUserPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JTextField userIdField = new JTextField();
        JTextField userNameField = new JTextField();
        JTextField userRoleField = new JTextField();
        JButton addUserBtn = new JButton("Add User");
        addUserBtn.setBackground(Color.GREEN);
        addUserBtn.setForeground(Color.WHITE);
        JTextArea addUserOutput = new JTextArea(2, 20);
        addUserOutput.setEditable(false);
        addUserOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        addUserPanel.add(new JLabel("User ID:")); addUserPanel.add(userIdField);
        addUserPanel.add(new JLabel("Name:")); addUserPanel.add(userNameField);
        addUserPanel.add(new JLabel("Role:")); addUserPanel.add(userRoleField);
        addUserPanel.add(addUserBtn); addUserPanel.add(new JScrollPane(addUserOutput));
        addUserBtn.addActionListener(e -> {
            lib.addUser(userIdField.getText(), userNameField.getText(), userRoleField.getText());
            addUserOutput.setText("User added successfully!");
        });

        // --- Add Book ---
        JPanel addBookPanel = new JPanel(new GridLayout(5, 2, 10, 10));
        addBookPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JTextField bookIdField = new JTextField();
        JTextField bookTitleField = new JTextField();
        JTextField bookAuthorField = new JTextField();
        JButton addBookBtn = new JButton("Add Book");
        addBookBtn.setBackground(Color.GREEN);
        addBookBtn.setForeground(Color.WHITE);
        JTextArea addBookOutput = new JTextArea(2, 20);
        addBookOutput.setEditable(false);
        addBookOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        addBookPanel.add(new JLabel("Book ID:")); addBookPanel.add(bookIdField);
        addBookPanel.add(new JLabel("Title:")); addBookPanel.add(bookTitleField);
        addBookPanel.add(new JLabel("Author:")); addBookPanel.add(bookAuthorField);
        addBookPanel.add(addBookBtn); addBookPanel.add(new JScrollPane(addBookOutput));
        addBookBtn.addActionListener(e -> {
            lib.addBook(bookIdField.getText(), bookTitleField.getText(), bookAuthorField.getText());
            addBookOutput.setText("Book added successfully!");
        });

        // --- Borrow Book ---
        JPanel borrowPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        borrowPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JTextField borrowUserId = new JTextField();
        JTextField borrowBookId = new JTextField();
        JButton borrowBtn = new JButton("Borrow Book");
        borrowBtn.setBackground(Color.ORANGE);
        borrowBtn.setForeground(Color.BLACK);
        JTextArea borrowOutput = new JTextArea(2, 20);
        borrowOutput.setEditable(false);
        borrowOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        borrowPanel.add(new JLabel("User ID:")); borrowPanel.add(borrowUserId);
        borrowPanel.add(new JLabel("Book ID:")); borrowPanel.add(borrowBookId);
        borrowPanel.add(borrowBtn); borrowPanel.add(new JScrollPane(borrowOutput));
        borrowBtn.addActionListener(e -> borrowOutput.setText(lib.borrowBook(borrowUserId.getText(), borrowBookId.getText())));

        // --- Return Book ---
        JPanel returnPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        returnPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JTextField returnUserId = new JTextField();
        JTextField returnBookId = new JTextField();
        JButton returnBtn = new JButton("Return Book");
        returnBtn.setBackground(Color.ORANGE);
        returnBtn.setForeground(Color.BLACK);
        JTextArea returnOutput = new JTextArea(2, 20);
        returnOutput.setEditable(false);
        returnOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        returnPanel.add(new JLabel("User ID:")); returnPanel.add(returnUserId);
        returnPanel.add(new JLabel("Book ID:")); returnPanel.add(returnBookId);
        returnPanel.add(returnBtn); returnPanel.add(new JScrollPane(returnOutput));
        returnBtn.addActionListener(e -> returnOutput.setText(lib.returnBook(returnUserId.getText(), returnBookId.getText())));

        // --- List Users (Table) ---
        JPanel listUsersPanel = new JPanel(new BorderLayout());
        listUsersPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        String[] userColumns = {"ID", "Name", "Role", "Fine"};
        DefaultTableModel userTableModel = new DefaultTableModel(userColumns, 0);
        JTable userTable = new JTable(userTableModel);
        userTable.setFillsViewportHeight(true);
        userTable.setRowHeight(25);
        JScrollPane userScroll = new JScrollPane(userTable);
        JButton refreshUsers = new JButton("Refresh Users");
        refreshUsers.setBackground(Color.CYAN);
        refreshUsers.setForeground(Color.BLACK);
        refreshUsers.addActionListener(e -> {
            userTableModel.setRowCount(0);
            for (User u : lib.getUsers()) userTableModel.addRow(u.toTableRow());
        });
        listUsersPanel.add(userScroll, BorderLayout.CENTER);
        listUsersPanel.add(refreshUsers, BorderLayout.SOUTH);

        // --- List Books (Table) ---
        JPanel listBooksPanel = new JPanel(new BorderLayout());
        listBooksPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        String[] bookColumns = {"ID", "Title", "Author", "Status", "Waitlist"};
        DefaultTableModel bookTableModel = new DefaultTableModel(bookColumns, 0);
        JTable bookTable = new JTable(bookTableModel);
        bookTable.setFillsViewportHeight(true);
        bookTable.setRowHeight(25);
        JScrollPane bookScroll = new JScrollPane(bookTable);
        JButton refreshBooks = new JButton("Refresh Books");
        refreshBooks.setBackground(Color.CYAN);
        refreshBooks.setForeground(Color.BLACK);
        refreshBooks.addActionListener(e -> {
            bookTableModel.setRowCount(0);
            for (Book b : lib.getBooks()) bookTableModel.addRow(b.toTableRow());
        });
        listBooksPanel.add(bookScroll, BorderLayout.CENTER);
        listBooksPanel.add(refreshBooks, BorderLayout.SOUTH);

        // Add tabs
        tabs.add("Add User", addUserPanel);
        tabs.add("Add Book", addBookPanel);
        tabs.add("Borrow Book", borrowPanel);
        tabs.add("Return Book", returnPanel);
        tabs.add("List Users", listUsersPanel);
        tabs.add("List Books", listBooksPanel);

        add(tabs);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LibrarySystemGUI::new);
    }
}
