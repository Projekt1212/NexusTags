package org.fahri.nexusone;

import java.util.ArrayList;
import java.util.List;

public class TagEditorSession {
    public String tagId;
    public String display;
    public String permission;
    public int price;
    public String icon;
    public String rarity;
    public List<String> description;
    public String lastEditAction; // Untuk melacak apa yang sedang diinput di chat

    public TagEditorSession(String tagId) {
        this.tagId = tagId;
        this.display = "&f" + tagId;
        this.permission = "";
        this.price = 0;
        this.icon = "PAPER";
        this.rarity = "COMMON";
        this.description = new ArrayList<>();
        this.lastEditAction = "";
    }
}