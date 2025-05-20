package com.example.noten;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MyProjectsFragment extends Fragment {

    private ListView listViewProjects;
    private List<Project> projects = new ArrayList<>();
    private ArrayAdapter<Project> adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_my_projects, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        listViewProjects = view.findViewById(R.id.listViewProjects);
        adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, projects);
        listViewProjects.setAdapter(adapter);
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("projects")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    projects.clear();
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        String name = doc.getString("name");
                        String imageUrl = doc.getString("imageUrl");
                        projects.add(new Project(name, "", imageUrl));
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Toast.makeText(getActivity(),
                        "Error loading projects: "+e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
