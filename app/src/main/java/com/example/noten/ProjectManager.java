package com.example.noten;

import java.util.ArrayList;
import java.util.List;

public class ProjectManager {
    private static ArrayList<Project> projects = new ArrayList<>();

    public static void addProject(Project project) {
        projects.add(project);
    }

    public static List<Project> getProjects() {
        return projects;
    }
}
