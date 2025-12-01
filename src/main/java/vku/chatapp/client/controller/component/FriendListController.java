package vku.chatapp.client.controller.component;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.service.FriendService;
import vku.chatapp.common.dto.UserDTO;

import java.util.List;

public class FriendListController {
    @FXML private ListView<UserDTO> friendListView;
    @FXML private TextField searchField;
    @FXML private Button addFriendButton;

    private FriendService friendService;
    private ObservableList<UserDTO> friends;

    @FXML
    public void initialize() {
        friendService = new FriendService();
        friends = FXCollections.observableArrayList();

        friendListView.setItems(friends);
        friendListView.setCellFactory(lv -> new FriendListCell());

        setupSearch();
        loadFriends();
    }

    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterFriends(newVal);
        });
    }

    private void loadFriends() {
        Long currentUserId = UserSession.getInstance().getCurrentUser().getId();

        new Thread(() -> {
            List<UserDTO> friendList = friendService.getFriendList(currentUserId);

            javafx.application.Platform.runLater(() -> {
                friends.clear();
                friends.addAll(friendList);
            });
        }).start();
    }

    private void filterFriends(String query) {
        if (query == null || query.isEmpty()) {
            loadFriends();
            return;
        }

        // Filter existing list
        String lowerQuery = query.toLowerCase();
        friends.removeIf(friend ->
                !friend.getDisplayName().toLowerCase().contains(lowerQuery) &&
                        !friend.getUsername().toLowerCase().contains(lowerQuery)
        );
    }

    @FXML
    private void handleAddFriend() {
        // TODO: Show add friend dialog
    }

    private static class FriendListCell extends ListCell<UserDTO> {
        private HBox content;
        private Label nameLabel;
        private Label statusLabel;

        public FriendListCell() {
            content = new HBox(10);
            content.setStyle("-fx-padding: 10px;");

            VBox textBox = new VBox(2);
            HBox.setHgrow(textBox, Priority.ALWAYS);

            nameLabel = new Label();
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            statusLabel = new Label();
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #16c60c;");

            textBox.getChildren().addAll(nameLabel, statusLabel);
            content.getChildren().add(textBox);
        }

        @Override
        protected void updateItem(UserDTO friend, boolean empty) {
            super.updateItem(friend, empty);

            if (empty || friend == null) {
                setGraphic(null);
            } else {
                nameLabel.setText(friend.getDisplayName());
                statusLabel.setText(friend.getStatus().toString());
                setGraphic(content);
            }
        }
    }
}
