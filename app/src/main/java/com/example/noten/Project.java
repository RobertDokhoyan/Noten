package com.example.noten;

public class Project {
    private String name;
    private String pdfPath;
    private String imagePath;

    public Project(String name, String pdfPath, String imagePath) {
        this.name = name;
        this.pdfPath = pdfPath;
        this.imagePath = imagePath;
    }

    public String getName() {
        return name;
    }

    public String getPdfPath() {
        return pdfPath;
    }

    public String getImagePath() {
        return imagePath;
    }

    @Override
    public String toString() {
        return name; // Для простого отображения в ArrayAdapter
    }
}
