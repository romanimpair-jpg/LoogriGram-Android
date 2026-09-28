package org.telegram.ui.Stories;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_stories;

// LoogriGram: all that is left of the stories utilities. Stories are removed,
// as on desktop; the data layer still ages stories out with isExpired.
public class StoriesUtilities {

    public static boolean isExpired(int currentAccount, TL_stories.StoryItem storyItem) {
        return ConnectionsManager.getInstance(currentAccount).getCurrentTime() > storyItem.expire_date;
    }
}
