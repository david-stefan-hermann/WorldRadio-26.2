package worldradio.item;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import worldradio.WorldRadio;

/**
 * The guide book: every block, its recipe and how the signal travels, in its own screen (the content is
 * {@link GuideBook}). Every player gets one book once, the first time they join after the mod is installed.
 */
public class GuideBookItem extends Item {
    /** Set once the player has had the book; kept through death (vanilla entity tags are not). */
    public static final AttachmentType<Boolean> GOT_BOOK = AttachmentRegistry.create(WorldRadio.id("got_guide_book"),
            b -> b.persistent(Codec.BOOL).copyOnDeath());

    /** Set by the client entry point; the server side never opens screens. */
    public static Runnable screenOpener = () -> {
    };

    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) screenOpener.run();
        return InteractionResult.SUCCESS;
    }

    /** The welcome gift: the book, once per player. */
    public static void giveBookOnce(ServerPlayer player) {
        if (player.hasAttached(GOT_BOOK)) return;
        ItemStack book = new ItemStack(WorldRadio.GUIDE_BOOK_ITEM);
        if (!player.getInventory().add(book)) player.drop(book, false);
        player.setAttached(GOT_BOOK, true);
    }
}
