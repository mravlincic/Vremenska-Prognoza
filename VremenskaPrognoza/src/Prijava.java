import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.awt.*;

import org.ini4j.Ini;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;


public class Prijava extends JFrame {
    private JTextField korisnickoime;
    private JTextField zaporka;
    private JPanel prijavaForm;
    private JButton registracijaBtn;
    private JButton prijavaBtn;
    private JCheckBox zapamtiMeCBox;
    private JLabel prijavaLbl;
    private JLabel korisnickoimeLbl;
    private JLabel dobrodosliLbl;
    private JLabel zaporkaLbl;
    private JComboBox jezikCBox;
    private JLabel jezikLbl;

    Locale defLocale;

    //windows registar
    private static final String REGISTRY_KEY = "HKEY_CURRENT_USER\\Software\\VremenskaPrognoza";
    private static final String[] REGISTRY_VALUES = {"window_x", "window_y", "window_width", "window_height"};

    //ini datoteka
    private static final String FILE_PATH = "postavke.ini";

    private static final int[] papri = new int[100];

    public Prijava() throws SQLException, ClassNotFoundException {
        FlatLightLaf.setup();
        setContentPane(prijavaForm);
        setTitle("Vremenska Prognoza");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setVisible(true);

        //opcije comboboxa
        jezikCBox.addItem("HR");
        jezikCBox.addItem("EN");

        //dodavanje vrijednosti papra
        for (int i = 0; i < 100; i++) {
            papri[i] = i;
        }
        ;

        //ucitavanje ini datoteke
        String[] iniVraceno = ucitajINI();

        //postavljanje vrijednosti ukoliko je zapamtime opcija true
        Boolean zapamtime = Objects.equals(iniVraceno[3], "true");
        jezikCBox.setSelectedItem(iniVraceno[4]);
        zapamtiMeCBox.setSelected(zapamtime);

        //ucitavanje jezika
        defLocale = new Locale(iniVraceno[4].toLowerCase(), iniVraceno[4]);
        ResourceBundle defBundle = ResourceBundle.getBundle("bundle", defLocale);

        dobrodosliLbl.setText(defBundle.getString("dobrodosliLbl"));
        korisnickoimeLbl.setText(defBundle.getString("korisnickoimeLbl"));
        zaporkaLbl.setText(defBundle.getString("zaporkaLbl"));
        prijavaBtn.setText(defBundle.getString("prijavaBtn"));
        registracijaBtn.setText(defBundle.getString("registracijaBtn"));
        zapamtiMeCBox.setText(defBundle.getString("zapamtiMeCBox"));
        prijavaLbl.setText(defBundle.getString("prijavaLbl"));
        jezikLbl.setText(defBundle.getString("jezikLbl"));
        String prijavaUnosE = defBundle.getString("prijavaUnosE");
        String prijavaNeuspjehE = defBundle.getString("prijavaNeuspjehE");
        String prijavaNepostojiE = defBundle.getString("prijavaNepostojiE");
        String bazaPristupE = defBundle.getString("bazaPristupE");
        String registracijaPostojiE = defBundle.getString("registracijaPostojiE");
        String registracijaUspjehE = defBundle.getString("registracijaUspjehE");
        String registracijaNeuspjehE = defBundle.getString("registracijaNeuspjehE");
        String odabirSlikeE = defBundle.getString("odabirSlikeE");

        //postavljanje podataka korisnika
        if (zapamtime) {
            korisnickoime.setText(iniVraceno[0]);
            zaporka.setText(iniVraceno[1]);
            prijavaLbl.setText(prijavaLbl.getText() + " " + iniVraceno[2]);
        } else {
            prijavaLbl.setText(prijavaLbl.getText() + " -");
        }

        //ucitavanje postavki prozora
        Rectangle prozor = ucitajPostavkeProzora();
        if (prozor != null) {
            setBounds(prozor);
        }

        //spremanje postavki na zatvaranju
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                spremiPostavkeProzora(getBounds());
                spremiINI(korisnickoime.getText(), napraviHash(korisnickoime.getText(), zaporka.getText()), formatiranoVrijeme(), Boolean.toString(zapamtiMeCBox.isSelected()), (String) jezikCBox.getSelectedItem());
            }
        });

        // JDBC drivera i veza za bazu
        Class.forName("org.sqlite.JDBC");
        Connection dbveza = DriverManager.getConnection("jdbc:sqlite:src/Baza.db");

        //promjena jezika za sljedece dijaloge nakon odabira
        jezikCBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String odabraniJezik = (String) jezikCBox.getSelectedItem();
                defLocale = new Locale(odabraniJezik.toLowerCase(), odabraniJezik);

            }
        });

        //gumb prijava
        prijavaBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // dobivanje unesenih vrijednosti tekstualnih polja
                String korisnickoimeText = korisnickoime.getText();
                String zaporkaText = zaporka.getText();

                //provjera jesu li uneseni svi podaci
                if (korisnickoimeText.isEmpty() || zaporkaText.isEmpty()) {
                    JOptionPane.showMessageDialog(Prijava.this, prijavaUnosE, "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                try {
                    //provjera je li korisnicko ime u bazi podataka
                    String upit = "SELECT * FROM Korisnici WHERE korisnickoime = ?";
                    try (PreparedStatement statement = dbveza.prepareStatement(upit)) {
                        statement.setString(1, korisnickoimeText);
                        try (ResultSet rezultatUpita = statement.executeQuery()) {
                            if (rezultatUpita.next()) {
                                //ukoliko korisnik postoji, provjerava se zaporka
                                String pohranjeniHash = rezultatUpita.getString("zaporka");
                                int ulogaId = rezultatUpita.getInt("uloga_id");
                                byte[] slika = rezultatUpita.getBytes("slika");
                                int korisnik_id = rezultatUpita.getInt("id");
                                String trenutniHash = napraviHash(korisnickoimeText, zaporkaText);

                                if (validirajHash(korisnickoimeText, zaporkaText, pohranjeniHash) || pohranjeniHash.equals(trenutniHash)) {
                                    spremiINI(korisnickoimeText, trenutniHash, formatiranoVrijeme(), Boolean.toString(zapamtiMeCBox.isSelected()), (String) jezikCBox.getSelectedItem());

                                    //zatvori postojeci i otvori sljedeci prozor
                                    dispose();

                                    if (ulogaId == 2) {
                                        Glavni glavni = new Glavni(korisnickoimeText, korisnik_id, ulogaId, slika, defLocale, dbveza);
                                        glavni.setSize(getSize());
                                        glavni.setLocation(getLocation());
                                        glavni.setVisible(true);
                                    } else if (ulogaId == 1) {
                                        Admin admin = new Admin(dbveza, ulogaId, defLocale);
                                        admin.setSize(getSize());
                                        admin.setLocation(getLocation());
                                        admin.setVisible(true);
                                    }
                                } else {
                                    JOptionPane.showMessageDialog(Prijava.this, prijavaNeuspjehE, "Error", JOptionPane.ERROR_MESSAGE);
                                }
                            } else {
                                JOptionPane.showMessageDialog(Prijava.this, prijavaNepostojiE, "Error", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }
                } catch (SQLException ex) {
                    System.err.println("Error accessing the database: " + ex.getMessage());
                    JOptionPane.showMessageDialog(Prijava.this, bazaPristupE, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        //gumb registracija
        registracijaBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //dobivanje unesenih vrijednosti
                String korisnickoimeText = korisnickoime.getText();
                String zaporkaText = zaporka.getText();

                // provjera jesu li sva polja ispunjena
                if (korisnickoimeText.isEmpty() || zaporkaText.isEmpty()) {
                    JOptionPane.showMessageDialog(Prijava.this, prijavaUnosE, "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                try {
                    //je li korisnicko ime dostupno
                    String upit = "SELECT COUNT(*) AS count FROM Korisnici WHERE korisnickoime = ?";
                    try (PreparedStatement statement = dbveza.prepareStatement(upit)) {
                        statement.setString(1, korisnickoimeText);
                        try (ResultSet rezultatUpita = statement.executeQuery()) {
                            if (rezultatUpita.next() && rezultatUpita.getInt("count") > 0) {
                                JOptionPane.showMessageDialog(Prijava.this, registracijaPostojiE, "Error", JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                        }
                    }

                    //ucitavanje profilne slike
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setDialogTitle(odabirSlikeE);
                    int result = fileChooser.showOpenDialog(Prijava.this);
                    if (result != JFileChooser.APPROVE_OPTION) {
                        return; //ukoliko se nije odabrala slika
                    }
                    File odabranaDat = fileChooser.getSelectedFile();

                    // citanje slike
                    byte[] imageBytes = Files.readAllBytes(odabranaDat.toPath());

                    // sazimanje zaporke
                    String hashZaporke = napraviHash(korisnickoimeText, zaporkaText);

                    // spremanje podataka o novom korisniku u bazu
                    upit = "INSERT INTO Korisnici (korisnickoime, zaporka, uloga_id, slika) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement statement = dbveza.prepareStatement(upit)) {
                        statement.setString(1, korisnickoimeText);
                        statement.setString(2, hashZaporke);
                        statement.setInt(3, 2);
                        // spremanje slike kao blob
                        statement.setBytes(4, imageBytes);
                        int rowsAffected = statement.executeUpdate();
                        if (rowsAffected > 0) {
                            JOptionPane.showMessageDialog(Prijava.this, registracijaUspjehE, "Info", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(Prijava.this, registracijaNeuspjehE, "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                } catch (SQLException | IOException ex) {
                    System.err.println("Error accessing the database or reading the image file: " + ex.getMessage());
                    JOptionPane.showMessageDialog(Prijava.this, bazaPristupE, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });


    }

    public static String formatiranoVrijeme() {
        LocalDateTime trenutno = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy. HH:mm:ss");
        return trenutno.format(formatter);
    }

    public static void spremiINI(String korisnickoime, String zaporka, String vrijeme, String zapamtime, String jezik) {
        try {
            //incijalizacija
            Ini ini = new Ini();
            // dodavanje sekcije i postavki
            if (Objects.equals(zapamtime, "true")) {
                ini.put("Postavke", "korisnickoime", korisnickoime);
                ini.put("Postavke", "zaporka", zaporka);
                ini.put("Postavke", "vrijeme", vrijeme);
            } else {
                ini.put("Postavke", "korisnickoime", "");
                ini.put("Postavke", "zaporka", "");
                ini.put("Postavke", "vrijeme", "");
            }
            ini.put("Postavke", "zapamtime", zapamtime);
            ini.put("Postavke", "jezik", jezik);
            // spremanje u ini datoteku
            ini.store(new File(FILE_PATH));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static String[] ucitajINI() {
        try {
            //ucitavanje ini datoteke
            Ini ini = new Ini(new File(FILE_PATH));
            //dohvacanje postavki
            String korisnickoime = ini.get("Postavke", "korisnickoime");
            String zaporka = ini.get("Postavke", "zaporka");
            String vrijeme = ini.get("Postavke", "vrijeme");
            String zapamtime = ini.get("Postavke", "zapamtime");
            String jezik = ini.get("Postavke", "jezik");

            String[] vrijednosti = new String[5];
            vrijednosti[0] = korisnickoime;
            vrijednosti[1] = zaporka;
            vrijednosti[2] = vrijeme;
            vrijednosti[3] = zapamtime;
            vrijednosti[4] = jezik;
            return vrijednosti;
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }


    public static void spremiPostavkeProzora(Rectangle prozor) {
        try {
            //spremanje postavki u windows registar
            //reg add "REGISTRY_KEY" /v "REGISTRY_VALUE" /t REG_SZ /d "VALUE" /f
            Runtime.getRuntime().exec("reg add \"" + REGISTRY_KEY + "\" /v " + REGISTRY_VALUES[0] + " /t REG_SZ /d " + prozor.x + " /f");
            Runtime.getRuntime().exec("reg add \"" + REGISTRY_KEY + "\" /v " + REGISTRY_VALUES[1] + " /t REG_SZ /d " + prozor.y + " /f");
            Runtime.getRuntime().exec("reg add \"" + REGISTRY_KEY + "\" /v " + REGISTRY_VALUES[2] + " /t REG_SZ /d " + prozor.width + " /f");
            Runtime.getRuntime().exec("reg add \"" + REGISTRY_KEY + "\" /v " + REGISTRY_VALUES[3] + " /t REG_SZ /d " + prozor.height + " /f");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public Rectangle ucitajPostavkeProzora() {
        Rectangle prozor = new Rectangle();
        try {
            // prolazak kroz sve kljuceve registra i citanje vrijednosti
            for (int i = 0; i < REGISTRY_VALUES.length; i++) {
                Process proces = Runtime.getRuntime().exec("reg query \"" + REGISTRY_KEY + "\" /v " + REGISTRY_VALUES[i]);
                //citanje vrijednosti iz streama
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(proces.getInputStream()));
                String linija;
                while ((linija = bufferedReader.readLine()) != null) {
                    if (linija.contains(REGISTRY_VALUES[i])) {
                        String[] vrijednostiLinije = linija.split("\\s+"); //regex za razmak
                        String trazenaVrijednost = vrijednostiLinije[3];
                        switch (i) {
                            case 0:
                                prozor.x = Integer.parseInt(trazenaVrijednost);
                                break;
                            case 1:
                                prozor.y = Integer.parseInt(trazenaVrijednost);
                                break;
                            case 2:
                                prozor.width = Integer.parseInt(trazenaVrijednost);
                                break;
                            case 3:
                                prozor.height = Integer.parseInt(trazenaVrijednost);
                                break;
                        }
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return prozor;
    }


    public static String napraviHash(String korisnickoIme, String zaporka, String... paparArg) {
        // dinamicka sol je obrnuto ime
        String sol = new StringBuilder(korisnickoIme).reverse().toString();

        //postavljanje papra
        String papar;
        if (paparArg.length > 0) {
            // koristi se papar ako je zadani
            papar = paparArg[0];
        } else {
            // inace se uzima nasumican
            Random random = new SecureRandom();
            papar = String.valueOf(papri[random.nextInt(papri.length)]);
        }

        // string za sazimanje
        String pripremljeniString = zaporka + sol + papar;

        try {
            // instanca za sha-256 sazetak
            MessageDigest sazetak = MessageDigest.getInstance("SHA-256");

            // sazimanje
            byte[] hashedBytes = sazetak.digest(pripremljeniString.getBytes());

            // pretvorba byte arraya u hex
            StringBuilder hexString = new StringBuilder();
            for (byte hashedByte : hashedBytes) {
                String hex = Integer.toHexString(0xff & hashedByte);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean validirajHash(String korisnickoIme, String zaporka, String hashZaporke) {
        // iteracija kroz sve papre
        for (int papar : papri) {
            // stvaranje sazetka
            String generiraniHash = napraviHash(korisnickoIme, zaporka, String.valueOf(papar));

            // ukoliko se sazeci podudaraju vrati true
            if (generiraniHash != null && generiraniHash.equals(hashZaporke)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws SQLException, ClassNotFoundException {
        new Prijava();
    }

}

