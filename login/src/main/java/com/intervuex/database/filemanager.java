package com.securvote.database;

import java.io.*;
import java.security.PrivateKey;

public class filemanager {

    private static final String DIRECTORY = "privateKeys"; // Directory to save files

    // Static method to create the directory if it doesn't exist
    private static void createDirectory() {
        File directory = new File(DIRECTORY);
        if (!directory.exists()) {
            directory.mkdirs(); // Create the directory
            System.out.println("Directory '" + DIRECTORY + "' created.");
        }
    }

    // Static method to write a PrivateKey object to a binary file
    public static void writeToFile(String secretCode, PrivateKey privateKey) {
        createDirectory(); // Ensure the directory exists
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DIRECTORY + File.separator + secretCode + ".dat"))) {
            // Writing the PrivateKey object to the binary file
            oos.writeObject(privateKey);
            //System.out.println("PrivateKey written to the binary file '" + DIRECTORY + File.separator + secretCode + ".dat' successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Static method to read a PrivateKey object from a binary file
    public static PrivateKey retrievedContent(String secretCode) {
        PrivateKey privateKey = null;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(DIRECTORY + File.separator + secretCode + ".dat"))) {
            // Reading the PrivateKey object back
            privateKey = (PrivateKey) ois.readObject();
            //System.out.println("PrivateKey read from the binary file '" + DIRECTORY + File.separator + secretCode + ".dat' successfully.");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return privateKey;
    }

    // Static method to delete all files in the specified directory
    public static void deleteAllFiles() {
        File directory = new File(DIRECTORY);

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles(); // Get all files in the directory
            if (files != null) { // Check if files is not null
                for (File file : files) {
                    if (file.isFile()) {
                        if (file.delete()) {
                            System.out.println("Deleted file: " + file.getName());
                        } else {
                            System.out.println("Failed to delete file: " + file.getName());
                        }
                    }
                }
            } else {
                System.out.println("The directory is empty.");
            }
        } else {
            System.out.println("Directory does not exist: " + DIRECTORY);
        }
    }
}
