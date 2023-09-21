package com.banter;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.banter.Adapters.ChatAdapter;
import com.banter.Models.Messages;
import com.banter.Utils.EncryptDecryptHelper;
import com.banter.Utils.GsonUtils;
import com.banter.databinding.ActivityChatDetailBinding;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

public class ChatDetailActivity extends AppCompatActivity {
    ActivityChatDetailBinding binding;
    FirebaseDatabase database;
    FirebaseAuth auth;
    Context context;

    EncryptDecryptHelper encryptDecryptHelper = new EncryptDecryptHelper();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        database = FirebaseDatabase.getInstance();
        auth = FirebaseAuth.getInstance();
        binding.send.setEnabled(false);

        final String senderId = auth.getUid();
        String receiveId = getIntent().getStringExtra("userId");
        String userName = getIntent().getStringExtra("userName");
        String profilePic = getIntent().getStringExtra("profilePic");

        binding.userName.setText(userName);
        Picasso.get().load(profilePic).placeholder(R.drawable.man).into(binding.profileImage);

        binding.backArrow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ChatDetailActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });

        final ArrayList<Messages> messages = new ArrayList<>();
        final ChatAdapter chatAdapter = new ChatAdapter(messages, this, receiveId);
        binding.chatRecyclerView.setAdapter(chatAdapter);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.chatRecyclerView.setLayoutManager(layoutManager);

        final String senderRoom = senderId + receiveId;
        final String receiverRoom = receiveId + senderId;

        // Message Fetching from Database
        database.getReference().child("Chats")
                .child(senderRoom)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        messages.clear();
                        for (DataSnapshot snapshot1 : snapshot.getChildren()) {
                            String encryptedData = snapshot1.getValue(String.class);
                            try {
                                // Decrypt the encrypted data
                                String decryptedData = encryptDecryptHelper.decrypt(encryptedData);
                                // Convert the decrypted JSON data to MessageModel object
                                Messages model = GsonUtils.convertFromJson(decryptedData, Messages.class);
                                model.setMessageId(snapshot1.getKey());
                                messages.add(model);
//                                 model;

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        chatAdapter.notifyDataSetChanged();
                        if (messages.size() > 0) {
                            binding.chatRecyclerView.scrollToPosition(messages.size() - 1);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Handle database error
                    }
                });

        binding.send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String message = binding.btMessage.getText().toString();
                final Messages model = new Messages(senderId, message);
                model.setTimestamp(System.currentTimeMillis());
                binding.btMessage.setText("");
                // Encryption
                try {
                    // Convert the model object to JSON
                    String modelJson = GsonUtils.convertToJson(model);
                    // Encrypt the model data
                    String encryptedData = encryptDecryptHelper.encrypt(modelJson);
                    // Message store in Database
                    database.getReference().child("Chats")
                            .child(senderRoom)
                            .push()
                            .setValue(encryptedData)
                            .addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void unused) {
                                    database.getReference().child("Chats")
                                            .child(receiverRoom)
                                            .push()
                                            .setValue(encryptedData)
                                            .addOnSuccessListener(new OnSuccessListener<Void>() {
                                                @Override
                                                public void onSuccess(Void unused) {
                                                    // Data sent successfully
//                                                    updateRecentChat( senderRoom,receiverRoom,senderId,model);
                                                }
                                            });
                                }
                            });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        binding.btMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed in this case
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Enable the button if the user has typed something
                binding.send.setEnabled(s.length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not needed in this case
            }
        });
        binding.menuChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPopupMenu(view);
            }
        });
    }

    private void showPopupMenu(View view) {
        PopupMenu popupMenu = new PopupMenu(ChatDetailActivity.this, view);
        popupMenu.inflate(R.menu.chat_menu); // The menu resource file for the popup menu
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // Handle menu item clicks here
                int id = item.getItemId();
                if (id == R.id.Delete) {
                    deleteAllChats();
                    return true;

                } else {
                    return false;
                }
            }
        });
        popupMenu.show();
    }

    private void deleteAllChats() {
        final String senderId = auth.getUid();
        String receiveId = getIntent().getStringExtra("userId");
        final String senderRoom = senderId + receiveId;
        final String receiverRoom = receiveId + senderId;

        // Delete messages from sender's room
        database.getReference().child("Chats")
                .child(senderRoom)
                .removeValue()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        // Messages deleted from sender's room, now delete messages from receiver's room
                        Intent intetn = new Intent(ChatDetailActivity.this, MainActivity.class);
                        startActivity(intetn);
                        finish();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Handle the failure to delete messages from sender's room
                    }
                });

    }
//    private void updateRecentChat(String senderRoom, String receiverRoom, String senderId, Messages model){
//
//        // Encryption
//        try {
//
//            // Message store in Database
//            database.getReference().child("RecentChat")
//                    .child(senderRoom)
//                    .setValue(model)
//                    .addOnSuccessListener(new OnSuccessListener<Void>() {
//                        @Override
//                        public void onSuccess(Void unused) {
//                            database.getReference().child("RecentChat")
//                                    .child(receiverRoom)
//                                    .setValue(model)
//                                    .addOnSuccessListener(new OnSuccessListener<Void>() {
//                                        @Override
//                                        public void onSuccess(Void unused) {
//                                            // Data sent successfully
//                                        }
//                                    });
//                        }
//                    });
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//    }

}
