package com.example.noten;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;

import androidx.fragment.app.Fragment;

public class AvatarSelectionFragment extends Fragment {

    private GridView avatarGridView;
    private AvatarAdapter avatarAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_avatar_selection, container, false);
        avatarGridView = view.findViewById(R.id.avatar_grid_view);

        // Инициализация адаптера с ресурсами аватарок
        avatarAdapter = new AvatarAdapter(getContext(), new int[] {
                R.drawable.img_4, R.drawable.img_6, R.drawable.img_3,
                R.drawable.img_7, R.drawable.img_8, R.drawable.img_5
                // Здесь можно добавить больше аватарок
        });

        avatarGridView.setAdapter(avatarAdapter);

        avatarGridView.setOnItemClickListener((parent, view1, position, id) -> {
            // Выбираем аватар по клику
            selectAvatar(position);
        });

        return view;
    }

    private void selectAvatar(int position) {
        // Получаем URI выбранного аватара
        Uri avatarUri = Uri.parse("android.resource://" + getActivity().getPackageName() + "/" + avatarAdapter.getItem(position));

        // Отправляем результат в ProfileFragment
        Bundle result = new Bundle();
        result.putInt("selectedAvatar", avatarAdapter.getItem(position));
        getParentFragmentManager().setFragmentResult("avatarSelection", result);

        // Возвращаемся к предыдущему экрану
        getParentFragmentManager().popBackStack();
    }
}
