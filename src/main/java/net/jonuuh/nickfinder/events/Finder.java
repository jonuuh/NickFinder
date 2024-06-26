package net.jonuuh.nickfinder.events;

import net.jonuuh.nickfinder.config.Config;
import net.jonuuh.nickfinder.loggers.ChatLogger;
import net.jonuuh.nickfinder.loggers.FileLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.item.ItemEditableBook;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class Finder
{
    private final Minecraft mc;
    private final ChatLogger chatLogger;
    private final Config config;

    private FileLogger fileLoggerNicks;
    private FileLogger fileLoggerNicksLatest;
    private boolean running;
    private int nicks;
    private int ticks;
    private int curLobby;

    Finder(Minecraft mc, Config config, ChatLogger chatLogger)
    {
        this.mc = mc;
        this.config = config;
        this.chatLogger = chatLogger;
        this.running = false;
        this.nicks = 0;
        this.ticks = 0;
        this.curLobby = 0;
    }

    /**
     * Toggle the finder on or off.
     */
    public void toggle()
    {
        chatLogger.addLog("NickFinder toggled", EnumChatFormatting.GOLD, true);
        if (!running)
        {
            fileLoggerNicks = new FileLogger("nicks", true);
            fileLoggerNicksLatest = new FileLogger("nicks-latest", false);
            ticks = 0;
            nicks = 0;
            curLobby = 0; // TODO: make this the actual lobby /locraw
            MinecraftForge.EVENT_BUS.register(this);
            chatLogger.addLog("Start: new loggers, ticker + nicks reset, registered", EnumChatFormatting.YELLOW, false);
        }
        else
        {
            fileLoggerNicks.close();
            fileLoggerNicksLatest.close();
            MinecraftForge.EVENT_BUS.unregister(this);
            chatLogger.addLog("Stop: loggers closed, unregistered", EnumChatFormatting.YELLOW, false);
        }
        running = !running;
    }

    /**
     * Client tick event handler.<br>
     * - Used to perform certain actions periodically (requesting a new nick or preventing AFK).
     *
     * @param event the event
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase == TickEvent.Phase.START && !mc.isGamePaused())
        {
            ticks++;

            if (ticks % (20 * config.getAntiAFKDelaySecs()) == 0)
            {
                int randLobby = ThreadLocalRandom.current().nextInt(config.getLobbyMin(), config.getLobbyMax());
                randLobby = randLobby >= curLobby ? randLobby + 1 : randLobby; // silly style
                chatLogger.addLog("Swapping lobby from " + curLobby + " to " + randLobby + "...", EnumChatFormatting.GRAY, false);
                curLobby = randLobby;
                mc.thePlayer.sendChatMessage("/swaplobby " + randLobby);
            }

            if (ticks % (20 * config.getReqNickDelaySecs()) == 0)
            {
                chatLogger.addLog("Requesting new nick...", EnumChatFormatting.GRAY, false);
                mc.thePlayer.sendChatMessage("/nick help setrandom");
            }
        }
    }

    /**
     * GUI opened event handler.<br>
     * - The "heart" of the mod, gets a nick from a written book and uses it if its "desirable".
     *
     * @param event the event
     */
    @SubscribeEvent
    public void onGUIOpened(GuiOpenEvent event)
    {
        if (event.gui instanceof GuiScreenBook)
        {
            ItemStack bookItem = mc.thePlayer.getHeldItem();
            if (bookItem.getItem() instanceof ItemEditableBook)
            {
                String bookPagesString = bookItem.getTagCompound().getTagList("pages", 8).toString();
                if (bookPagesString.contains("generated a random username"))
                {
                    String nick = bookPagesString.substring(bookPagesString.indexOf("actuallyset") + "actuallyset".length() + 1, bookPagesString.indexOf("respawn") - 1);
                    chatLogger.addLog(++nicks + ": " + nick);
                    fileLoggerNicks.addLogLn(nick);
                    fileLoggerNicksLatest.addLogLn(nick);

                    for (Pattern pattern : config.getTargetRegexps())
                    {
                        if (pattern.matcher(nick).find())
                        {
                            mc.thePlayer.sendChatMessage("/nick actuallyset " + nick + " respawn");
                            toggle();
                        }
                    }
                }
            }
            event.setCanceled(true);
        }
    }

    /**
     * Client chat received event handler.<br>
     * - Toggles off the mod if the player was sent to limbo while it was running.
     *
     * @param event the event
     */
    @SubscribeEvent
    public void onClientChatReceived(ClientChatReceivedEvent event)
    {
        if (config.getLimboStrings().contains(event.message.getUnformattedText()))
        {
            toggle();
        }
    }

    /**
     * Client disconnection from server event handler.<br>
     * - Toggles off the mod if the player logged out while it was running.
     *
     * @param event the event
     */
    @SubscribeEvent
    public void onClientDisconnectionFromServer(ClientDisconnectionFromServerEvent event)
    {
        toggle();
    }
}
