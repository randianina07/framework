package com.example.utils;

public class MapFile {
    private String nom;
    private String extension;
    private Byte[] bytes;

    public MapFile() {
    }
    public String getNom() {
        return nom;
    }
    public void setNom(String nom) {
        this.nom = nom;
    }
    public String getExtension() {
        return extension;
    }
    public void setExtension(String extension) {
        this.extension = extension;
    }
    public Byte[] getBytes() {
        return bytes;
    }
    public void setBytes(Byte[] content) {
        this.bytes = content;
    }
}
