package org.installer;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.imageio.ImageIO;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

public class InstallerGUI extends JFrame
{
    private static final Dimension SCREEN_SIZE = Toolkit.getDefaultToolkit().getScreenSize();
    private static final int HEIGHT = 325, WIDTH = 600;
    private static final Font FONT = new Font( "Roboto", 0, 15 );
    //Change this string to your repo and it should fetch releases from it.
    private static final String repo = "HeliosMinecraft/HeliosClient";
    private JComboBox<String> releaseDropdown;
    private static Image fullLogoImage = null;
    private File destination;
    private final File defaultDirectory = new File(System.getProperty("user.home") + "/AppData/Roaming/.minecraft/mods");

    public InstallerGUI() {
        super("HeliosClient Installer v1");
        FlatLaf.registerCustomDefaultsSource("assets");
        FlatDarkLaf.setup();

        this.setBounds(SCREEN_SIZE.width / 2 - ( WIDTH / 2 ), SCREEN_SIZE.height / 2 - ( HEIGHT / 2 ), WIDTH, HEIGHT );
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        this.setLayout(null);
        this.setResizable(false);
        this.getContentPane().setBackground(new Color(0x3e3e3e));

        try {
            InputStream iconImageStream = Objects.requireNonNull(this.getClass().getResourceAsStream( "/assets/icon.png"));
            this.setIconImage(ImageIO.read(iconImageStream));

            InputStream logoImageStream = Objects.requireNonNull(this.getClass().getResourceAsStream( "/assets/fullLogo.png"));
            fullLogoImage = ImageIO.read(logoImageStream);
        } catch ( IOException e )
        {
            JOptionPane.showMessageDialog(this,"Error in processing logo and icon!");
            Runtime.getRuntime().halt(0);
        }

        // Create Swing components

        JLabel profileLabel = new JLabel( "Version Tag:" );
        profileLabel.setBounds( 50, 30, WIDTH/2 - 75, 25 );
        profileLabel.setFont( FONT );
        this.add( profileLabel );

        releaseDropdown = new JComboBox<>();
        releaseDropdown.setBounds( WIDTH/2 - 125, 30, 225, 30 );
        releaseDropdown.setOpaque(false);
        try{
            populateReleases();
        }catch (IOException e){
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,"Error while populating releases.");
        }

        this.add(releaseDropdown);


        JButton destinationButton = new JButton("Choose Destination folder");
        destinationButton.setBounds( 5, 75, WIDTH - 30, 25 );
        destinationButton.setFont(FONT);

        // Add functionality to the destination button
        destinationButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooser.setFont(FONT);
            if(defaultDirectory.exists()) {
                fileChooser.setCurrentDirectory(defaultDirectory);
            }
            int option = fileChooser.showOpenDialog(InstallerGUI.this);
            if (option == JFileChooser.APPROVE_OPTION) {
                destination = fileChooser.getSelectedFile();
                if(destination != null) {
                    JOptionPane.showMessageDialog(this, "Destination file selected.");
                    destinationButton.setText(destination.getAbsolutePath());
                }
            }
        });

        this.add(destinationButton);

        JLabel progressBarLabel = new JLabel( "Progress Bar:" );
        progressBarLabel.setFont(FONT);
        progressBarLabel.setBounds(5,125,WIDTH - 30,25);
        this.add(progressBarLabel);

        JProgressBar bar = new JProgressBar();
        bar.setFont(FONT);
        bar.setBounds(5,150,WIDTH - 30,25);
        this.add(bar);

        JButton installButton = new JButton("Install");
        // Add functionality to the installation button
        installButton.addActionListener(e -> {
            String selectedRelease = (String) releaseDropdown.getSelectedItem();

            if(selectedRelease == null)return;

            selectedRelease = selectedRelease.substring(0,selectedRelease.lastIndexOf(" /"));

            if(destination == null){
                JOptionPane.showMessageDialog(this,"Destination folder is not selected!");
            }else {
                // Download and install the selected release to the specified destination
                try {
                    downloadAndInstall(selectedRelease, destination, bar);
                    JOptionPane.showMessageDialog(this, "HeliosClient has been successfully installed. Enjoy :)");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Error while downloading heliosclient :(");
                    ex.printStackTrace();
                }
            }
        });
        installButton.setBounds(WIDTH/2 - 125, 250, 250, 30);
        installButton.setFocusPainted( false );
        installButton.setFont(FONT);
        this.add(installButton);

        JLabel imageHolder = new JLabel(new ImageIcon(fullLogoImage.getScaledInstance(200,100,2)));
        imageHolder.setBounds(WIDTH/2 - 125, 160, 200, 100);
        this.add(imageHolder);

        // Set the frame to visible
        this.setVisible(true);
    }
    private void populateReleases() throws IOException {
        URL url = new URL("https://api.github.com/repos/" + repo + "/releases");

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        JsonReader jsonReader = Json.createReader(reader);
        JsonArray releases = jsonReader.readArray();

        for (JsonObject release : releases.getValuesAs(JsonObject.class)) {
            String tagName = release.getString("tag_name");
            //Append tag name as well for ease
            releaseDropdown.addItem(tagName + " / (" + release.getString("name") + ")" );
        }

        reader.close();
        jsonReader.close();
    }

    private void downloadAndInstall(String release, File destination,JProgressBar bar) throws IOException {
        URL url = new URL("https://api.github.com/repos/" + repo + "/releases/tags/" + release);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        JsonReader jsonReader = Json.createReader(reader);
        JsonObject releaseInfo = jsonReader.readObject();

        String downloadUrl = releaseInfo.getJsonArray("assets").getJsonObject(0).getString("browser_download_url");
        URL download = new URL(downloadUrl);
        HttpURLConnection httpConn = (HttpURLConnection) download.openConnection();
        int fileSize = httpConn.getContentLength();


        //Download URL has a problem
        if(downloadUrl.lastIndexOf("/") != -1) {

            //Get file name, which is usually at the end of the download url, after the tag name.
            String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));


            //If there is an issue with the file name then abort immediately as we do not want any corrupted
            //files  or directory to be created or stored.
            if (fileName == null || fileName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Download was aborted to protect you from damaged files or directory. Please download the file from our github");
                return;
            }

            try (BufferedInputStream in = new BufferedInputStream(httpConn.getInputStream());
                 FileOutputStream fileOutputStream = new FileOutputStream(destination.getPath() + fileName)) {
                byte[] dataBuffer = new byte[1024];
                long bytesRead = 0;
                int bytesReadChunk;
                while ((bytesReadChunk = in.read(dataBuffer, 0, 1024)) != -1) {
                    bytesRead += bytesReadChunk;
                    fileOutputStream.write(dataBuffer, 0, bytesReadChunk);

                    // update progress bar
                    double progress = bytesRead * 1.0 / fileSize * 100;
                    bar.setValue((int) progress);
                }
            }
        }else{
            JOptionPane.showMessageDialog(this, "Download URL seems incorrect or broken. URL we got:- "+downloadUrl);
            return;
        }
    }


    public static void main(String[] args) {
        new InstallerGUI();
    }

}
