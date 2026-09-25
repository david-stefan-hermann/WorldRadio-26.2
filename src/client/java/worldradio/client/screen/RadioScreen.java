package worldradio.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import worldradio.block.RadioBlockEntity;
import worldradio.client.FavouritesCache;
import worldradio.client.api.RadioBrowser;
import worldradio.client.audio.StationStream;
import worldradio.client.audio.StreamPool;
import worldradio.net.Packets;
import worldradio.server.FavouritesData.Favourite;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The station screen of a radio: the current station with its stream state, song title and range, a star to add it to
 * the player's favorites, Clear, a Turn off/Turn on key and the radio's volume at the bottom, and four tabs to pick a
 * station: Favorites (renamable), Browse (country → region → station from Radio-Browser, with a filter), URL (any
 * http(s) MP3 stream or playlist) and Search (station name or genre).
 */
public class RadioScreen extends net.minecraft.client.gui.screens.Screen {
    private static final int WIDTH = 340;
    /** Fits the 240 GUI pixels of a 720p window at automatic GUI scale. */
    private static final int HEIGHT = 236;
    /** Station name, stream status and range line. */
    private static final int HEADER = 46;
    /** The volume slider starts right of the Clear and on/off keys. */
    private static final int FOOTER_X = 148;
    /** Ticks between two volume packets while the slider is dragged. */
    private static final int VOLUME_SEND_EVERY = 4;

    public enum Tab { FAVOURITES, BROWSE, URL, SEARCH }

    private enum Step { COUNTRIES, REGIONS, STATIONS }

    private static Tab lastTab = Tab.FAVOURITES;
    /** Dev screenshots only: open this country code (all regions) as soon as the countries are loaded. */
    public static String devCountry;
    /** Dev screenshots only: type this into the search box when the Search tab opens. */
    public static String devSearch;
    /** Ticks without typing before a search runs (400 ms). */
    private static final int SEARCH_DELAY = 8;
    private static final int SEARCH_MIN = 2;

    private final BlockPos pos;
    private final StationList list = new StationList();
    private Tab tab;
    private Step step = Step.COUNTRIES;
    private RadioBrowser.Country country;
    private RadioBrowser.Region region;
    private List<RadioBrowser.Country> countries = List.of();
    private List<RadioBrowser.Region> regions = List.of();
    private List<RadioBrowser.Station> stations = List.of();
    private List<RadioBrowser.Station> searchResults = List.of();
    private String searched = "";
    private int searchCountdown = -1;
    private EditBox searchBox;
    private String loading = "";
    private String error = "";
    private int request;
    private int favouritesVersion = -1;
    /** The station the list marks, and what the list shows (a new list starts at the top). */
    private String listedUrl = "";
    private String listKey = "";
    private int left;
    private int top;
    private EditBox filter;
    private EditBox urlBox;
    private KeyButton star;
    private KeyButton power;
    private KeyButton back;
    private VolumeSlider volume;
    /** The slider's value while this screen is open (-1: not touched yet, take the radio's). */
    private double volumeValue = -1;
    private float volumeToSend = -1;
    private int volumeCooldown;
    /** The favorite being renamed and its text box. */
    private String renameUrl;
    private EditBox renameBox;

    public RadioScreen(BlockPos pos) {
        this(pos, lastTab);
    }

    public RadioScreen(BlockPos pos, Tab tab) {
        super(Component.translatable("block.worldradio.radio"));
        this.pos = pos;
        this.tab = tab;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        String language = minecraft.options.languageCode;
        RadioBrowser.languageCountry = language.contains("_") ? language.substring(language.indexOf('_') + 1).toUpperCase(Locale.ROOT) : "";
        String oldFilter = filter == null ? "" : filter.getValue();
        String oldUrl = urlBox == null ? "" : urlBox.getValue();
        String oldSearch = searchBox == null ? "" : searchBox.getValue();
        renameUrl = null;
        renameBox = null;

        star = addRenderableWidget(new KeyButton(left + WIDTH - 28, top + 7, 20, 20, Component.literal("☆"), b -> toggleFavourite()));
        Tab[] tabs = Tab.values();
        int tabWidth = (WIDTH - 16 - 4 * (tabs.length - 1)) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            KeyButton button = addRenderableWidget(new KeyButton(left + 8 + i * (tabWidth + 4), top + HEADER + 6, tabWidth, 20,
                    Component.translatable("worldradio.tab." + t.name().toLowerCase(Locale.ROOT)), b -> switchTab(t)));
            if (t == Tab.BROWSE || t == Tab.SEARCH) button.tooltip(Component.translatable("worldradio.browse.mp3_only"));
            button.active = t != tab;
        }
        int contentTop = top + HEADER + 32;
        int contentBottom = top + HEIGHT - 30;
        filter = null;
        urlBox = null;
        searchBox = null;
        back = null;
        switch (tab) {
            case FAVOURITES -> list.setBounds(left + 8, contentTop, WIDTH - 16, contentBottom - contentTop);
            case BROWSE -> {
                back = addRenderableWidget(new KeyButton(left + 8, contentTop + 1, 50, 16,
                        Component.translatable("worldradio.browse.back"), b -> goBack()));
                back.active = step != Step.COUNTRIES;
                filter = addRenderableWidget(new EditBox(font, left + WIDTH - 8 - 130, contentTop + 2, 130, 14,
                        Component.translatable("worldradio.browse.filter")));
                filter.setHint(Component.translatable("worldradio.browse.filter"));
                filter.setMaxLength(64);
                filter.setValue(oldFilter);
                filter.setResponder(text -> refreshList());
                list.setBounds(left + 8, contentTop + 20, WIDTH - 16, contentBottom - contentTop - 20);
                if (countries.isEmpty() && step == Step.COUNTRIES) loadCountries();
            }
            case URL -> {
                urlBox = addRenderableWidget(new EditBox(font, left + 10, contentTop + 16, WIDTH - 20, 18,
                        Component.translatable("worldradio.url.label")));
                urlBox.setMaxLength(Packets.MAX_URL);
                urlBox.setHint(Component.literal("http://…"));
                urlBox.setValue(oldUrl.isEmpty() ? currentUrl() : oldUrl);
                addRenderableWidget(new KeyButton(left + WIDTH - 10 - 80, contentTop + 40, 80, 20,
                        Component.translatable("worldradio.url.play"), b -> playUrl()));
                setInitialFocus(urlBox);
            }
            case SEARCH -> {
                searchBox = addRenderableWidget(new EditBox(font, left + 10, contentTop + 2, WIDTH - 20, 16,
                        Component.translatable("worldradio.search.hint")));
                searchBox.setHint(Component.translatable("worldradio.search.hint"));
                searchBox.setMaxLength(64);
                searchBox.setValue(devSearch != null ? devSearch : oldSearch);
                if (devSearch != null) searchCountdown = 0;
                devSearch = null;
                searchBox.setResponder(text -> searchCountdown = SEARCH_DELAY);
                list.setBounds(left + 8, contentTop + 20, WIDTH - 16, contentBottom - contentTop - 20);
                setInitialFocus(searchBox);
            }
        }
        addRenderableWidget(new KeyButton(left + 8, top + HEIGHT - 24, 60, 18, Component.translatable("worldradio.station.clear"),
                b -> tune("", "", "")).tooltip(Component.translatable("worldradio.station.clear.tooltip")));
        power = addRenderableWidget(new KeyButton(left + 72, top + HEIGHT - 24, 70, 18,
                Component.translatable("worldradio.station.off"), b -> togglePower()));
        RadioBlockEntity radio = radio();
        if (volumeValue < 0) volumeValue = radio == null ? 1.0 : radio.volume();
        volume = addRenderableWidget(new VolumeSlider(left + FOOTER_X, top + HEIGHT - 24, WIDTH - FOOTER_X - 8, 18, volumeValue));
        volume.setTooltip(Tooltip.create(Component.translatable("worldradio.volume.tooltip")));
        favouritesVersion = -1;
        refreshList();
    }

    private void switchTab(Tab t) {
        tab = t;
        lastTab = t;
        rebuildWidgets();
    }

    @Override
    public void tick() {
        if (!(radio() instanceof RadioBlockEntity radio)) {
            onClose();
            return;
        }
        boolean favourite = !radio.url().isEmpty() && FavouritesCache.contains(radio.url());
        star.setMessage(Component.literal(favourite ? "★" : "☆"));
        star.active = !radio.url().isEmpty();
        star.setTooltip(Tooltip.create(Component.translatable(favourite ? "worldradio.favorite.remove" : "worldradio.favorite.add")));
        power.setMessage(Component.translatable(radio.enabled() ? "worldradio.station.off" : "worldradio.station.on"));
        // a new station or a changed list: mark the right row
        if (FavouritesCache.version() != favouritesVersion || !radio.url().equals(listedUrl)) refreshList();
        if (searchBox != null && searchCountdown >= 0 && searchCountdown-- == 0) runSearch();
        if (volumeCooldown > 0) volumeCooldown--;
        if (volumeToSend >= 0 && volumeCooldown == 0) {
            ClientPlayNetworking.send(new Packets.SetVolume(pos, volumeToSend));
            volumeToSend = -1;
            volumeCooldown = VOLUME_SEND_EVERY;
        }
    }

    /** Runs once the player stopped typing for {@link #SEARCH_DELAY} ticks. */
    private void runSearch() {
        String text = searchBox.getValue().trim();
        if (text.equals(searched)) return;
        searched = text;
        searchResults = List.of();
        if (text.length() < SEARCH_MIN) {
            request++;
            loading = "";
            error = "";
            refreshList();
            return;
        }
        load(RadioBrowser.search(text), result -> searchResults = result);
    }

    private RadioBlockEntity radio() {
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof RadioBlockEntity r ? r : null;
    }

    private String currentUrl() {
        RadioBlockEntity radio = radio();
        return radio == null ? "" : radio.url();
    }

    // ---- actions

    private void tune(String url, String name, String countryCode) {
        ClientPlayNetworking.send(new Packets.SetStation(pos, url, name, countryCode));
    }

    /** The key names the action: "Turn off" while on, "Turn on" while off. */
    private void togglePower() {
        RadioBlockEntity radio = radio();
        if (radio != null) ClientPlayNetworking.send(new Packets.SetEnabled(pos, !radio.enabled()));
    }

    /** Dev screenshots only. */
    public void devPressPower() {
        togglePower();
    }

    /** Dev screenshots only. */
    public void devPressStar() {
        toggleFavourite();
    }

    /** Dev tests only: clicks row {@code index} of the list like the player would. */
    public void devClickRow(int index) {
        list.click(list.x() + 10, list.rowTop(index) + 4);
    }

    /** Dev tests only: opens the rename box of favorite row {@code index}. */
    public void devStartRename(int index) {
        List<Favourite> favourites = FavouritesCache.list();
        if (tab == Tab.FAVOURITES && index < favourites.size()) startRename(index, favourites.get(index));
    }

    /** Dev tests only: moves the volume slider like a drag would. */
    public void devSetVolume(double value) {
        volume.set(value);
    }

    /** Dev tests only: the names of the rows marked as playing. */
    public String devMarkedRows() {
        return list.markedRows();
    }

    private void toggleFavourite() {
        RadioBlockEntity radio = radio();
        if (radio == null || radio.url().isEmpty()) return;
        if (FavouritesCache.contains(radio.url())) {
            FavouritesCache.remove(radio.url());
        } else {
            String name = radio.name().isEmpty() ? hostOf(radio.url()) : radio.name();
            FavouritesCache.add(new Favourite(name, radio.url(), radio.country()));
        }
    }

    private void startRename(int index, Favourite favourite) {
        commitRename();
        int rowTop = list.rowTop(index);
        if (rowTop < 0) return;
        renameUrl = favourite.url();
        renameBox = addRenderableWidget(new EditBox(font, list.x() + 1, rowTop, list.width() - 8, StationList.ROW,
                Component.translatable("worldradio.favorite.rename")));
        renameBox.setMaxLength(256);
        renameBox.setValue(favourite.name());
        setFocused(renameBox);
    }

    /** Keeps the typed name (Enter, a click elsewhere, scrolling). */
    private void commitRename() {
        if (renameBox == null) return;
        FavouritesCache.rename(renameUrl, renameBox.getValue());
        closeRename();
    }

    private void closeRename() {
        if (renameBox == null) return;
        removeWidget(renameBox);
        renameBox = null;
        renameUrl = null;
        refreshList();
    }

    private void playUrl() {
        String url = urlBox.getValue().trim();
        if (!Packets.isStreamUrl(url)) {
            error = Component.translatable("worldradio.url.invalid").getString();
            return;
        }
        error = "";
        tune(url, hostOf(url), "");
    }

    private void goBack() {
        if (step == Step.STATIONS) step = regions.isEmpty() ? Step.COUNTRIES : Step.REGIONS;
        else if (step == Step.REGIONS) step = Step.COUNTRIES;
        if (filter != null) filter.setValue("");
        error = "";
        loading = "";
        request++;
        rebuildWidgets();
    }

    private void loadCountries() {
        load(RadioBrowser.countries(), result -> {
            countries = result;
            if (devCountry != null) {
                result.stream().filter(c -> c.code().equalsIgnoreCase(devCountry)).findFirst().ifPresent(this::openCountry);
            }
        });
    }

    private void openCountry(RadioBrowser.Country c) {
        country = c;
        region = null;
        regions = List.of();
        stations = List.of();
        step = Step.REGIONS;
        if (filter != null) filter.setValue("");
        rebuildWidgets();
        load(RadioBrowser.regions(c), result -> {
            regions = result;
            if (result.isEmpty() || devCountry != null) {
                devCountry = null;
                openRegion(null);
            }
        });
    }

    private void openRegion(RadioBrowser.Region r) {
        region = r;
        stations = List.of();
        step = Step.STATIONS;
        if (filter != null) filter.setValue("");
        rebuildWidgets();
        load(RadioBrowser.stations(country, r), result -> stations = result);
    }

    private <T> void load(CompletableFuture<T> future, java.util.function.Consumer<T> onResult) {
        int id = ++request;
        loading = Component.translatable("worldradio.browse.loading").getString();
        error = "";
        refreshList();
        future.whenComplete((result, failure) -> minecraft.execute(() -> {
            if (id != request) return;
            loading = "";
            if (failure != null) error = Component.translatable("worldradio.browse.error", RadioBrowser.describe(failure)).getString();
            else onResult.accept(result);
            refreshList();
        }));
    }

    // ---- list contents

    private void refreshList() {
        favouritesVersion = FavouritesCache.version();
        List<StationList.Row> rows = new ArrayList<>();
        String current = currentUrl();
        listedUrl = current;
        String needle = filter == null ? "" : filter.getValue().trim().toLowerCase(Locale.ROOT);
        switch (tab) {
            case FAVOURITES -> {
                List<Favourite> favourites = FavouritesCache.list();
                for (int i = 0; i < favourites.size(); i++) {
                    Favourite f = favourites.get(i);
                    int index = i;
                    rows.add(new StationList.Row(f.name(), f.country(), f.url().equals(current),
                            () -> tune(f.url(), f.name(), f.country()), () -> startRename(index, f)));
                }
                list.setEmptyText(Component.translatable("worldradio.favorites.empty").getString());
            }
            case BROWSE -> {
                switch (step) {
                    case COUNTRIES -> {
                        for (RadioBrowser.Country c : countries) {
                            if (matches(needle, c.name(), c.code())) {
                                rows.add(new StationList.Row(c.name(), c.code() + "  " + c.stations(), false, () -> openCountry(c)));
                            }
                        }
                    }
                    case REGIONS -> {
                        if (!regions.isEmpty() && needle.isEmpty()) {
                            rows.add(new StationList.Row(Component.translatable("worldradio.browse.all_regions").getString(),
                                    "", false, () -> openRegion(null)));
                        }
                        for (RadioBrowser.Region r : regions) {
                            if (matches(needle, r.name())) {
                                rows.add(new StationList.Row(r.name(), String.valueOf(r.stations()), false, () -> openRegion(r)));
                            }
                        }
                    }
                    case STATIONS -> {
                        for (RadioBrowser.Station s : stations) {
                            if (!matches(needle, s.name(), s.tags(), s.state())) continue;
                            String right = s.bitrate() > 0 ? s.bitrate() + "k" : "";
                            rows.add(new StationList.Row(s.name(), right, s.url().equals(current),
                                    () -> tune(s.url(), s.name(), s.country())));
                        }
                    }
                }
                list.setEmptyText(!loading.isEmpty() ? loading : !error.isEmpty() ? error
                        : Component.translatable("worldradio.browse.nothing").getString());
            }
            case URL -> {
            }
            case SEARCH -> {
                for (RadioBrowser.Station st : searchResults) {
                    String tags = st.firstTags(2);
                    String right = st.country() + (tags.isEmpty() ? "" : " · " + tags)
                            + (st.bitrate() > 0 ? " · " + st.bitrate() + "k" : "");
                    rows.add(new StationList.Row(st.name(), right, st.url().equals(current),
                            () -> tune(st.url(), st.name(), st.country())));
                }
                list.setEmptyText(!loading.isEmpty() ? loading : !error.isEmpty() ? error
                        : searched.length() < SEARCH_MIN ? Component.translatable("worldradio.search.hint.empty").getString()
                        : Component.translatable("worldradio.browse.nothing").getString());
            }
        }
        String key = tab + "|" + step + "|" + (country == null ? "" : country.code()) + "|"
                + (region == null ? "" : region.name()) + "|" + searched + "|" + needle;
        if (!key.equals(listKey)) list.resetScroll();
        listKey = key;
        list.setRows(rows);
    }

    private static boolean matches(String needle, String... fields) {
        if (needle.isEmpty()) return true;
        for (String f : fields) {
            if (f != null && f.toLowerCase(Locale.ROOT).contains(needle)) return true;
        }
        return false;
    }

    static String hostOf(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? url : host;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    // ---- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        RadioUi.panel(g, RadioUi.RADIO_PANEL, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        RadioBlockEntity radio = radio();
        if (radio != null) {
            String name = radio.url().isEmpty() ? Component.translatable("worldradio.station.none").getString()
                    : radio.name().isEmpty() ? hostOf(radio.url()) : radio.name();
            g.text(font, RadioUi.ellipsize(font, name, WIDTH - 50), left + 8, top + 8, RadioUi.TEXT, false);
            StatusLine status = status(radio);
            g.text(font, RadioUi.ellipsize(font, status.text(), WIDTH - 50), left + 8, top + 20, status.colour(), false);
            RadioUi.rangeLine(g, font, radio.range(), radio.antenna(), left + 8, top + 32, mouseX, mouseY);
        }
        int contentTop = top + HEADER + 32;
        switch (tab) {
            case FAVOURITES, SEARCH -> list.extract(g, font, mouseX, mouseY);
            case BROWSE -> {
                String crumb = switch (step) {
                    case COUNTRIES -> Component.translatable("worldradio.browse.countries").getString();
                    case REGIONS -> country == null ? "" : country.name();
                    case STATIONS -> (country == null ? "" : country.code()) + " › "
                            + (region == null ? Component.translatable("worldradio.browse.all_regions").getString() : region.name());
                };
                int crumbX = left + 8 + (back != null ? 56 : 0);
                g.text(font, RadioUi.ellipsize(font, crumb, WIDTH - 16 - 56 - 136), crumbX, contentTop + 5, RadioUi.LIST_SOFT, false);
                list.extract(g, font, mouseX, mouseY);
            }
            case URL -> {
                g.text(font, Component.translatable("worldradio.url.label"), left + 10, contentTop + 5, RadioUi.LIST_SOFT, false);
                g.textWithWordWrap(font, Component.translatable("worldradio.url.hint"), left + 10, contentTop + 66,
                        WIDTH - 20, RadioUi.LIST_SOFT, false);
                if (!error.isEmpty()) g.text(font, error, left + 10, contentTop + 46, RadioUi.ERROR, false);
            }
        }
        // widgets (keys, slider, text boxes, the rename box) on top of the list
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private record StatusLine(String text, int colour) {
    }

    private StatusLine status(RadioBlockEntity radio) {
        if (radio.url().isEmpty()) return new StatusLine(Component.translatable("worldradio.status.tune").getString(), RadioUi.TEXT_SOFT);
        if (!radio.enabled()) return new StatusLine(Component.translatable("worldradio.status.disabled").getString(), RadioUi.WARN);
        StationStream stream = StreamPool.find(radio.url());
        if (stream == null) return new StatusLine(Component.translatable("worldradio.status.idle").getString(), RadioUi.TEXT_SOFT);
        return switch (stream.state()) {
            case PLAYING -> new StatusLine(stream.title().isEmpty()
                    ? Component.translatable("worldradio.status.playing").getString()
                    : "♪ " + stream.title(), RadioUi.ACCENT);
            case CONNECTING -> new StatusLine(Component.translatable("worldradio.status.connecting").getString(), RadioUi.TEXT_SOFT);
            case RETRYING -> new StatusLine(Component.translatable("worldradio.status.retrying", stream.detail()).getString(), RadioUi.WARN);
            case UNSUPPORTED -> new StatusLine(Component.translatable("worldradio.status.unsupported", stream.detail()).getString(), RadioUi.ERROR);
            case CLOSED -> new StatusLine(Component.translatable("worldradio.status.idle").getString(), RadioUi.TEXT_SOFT);
        };
    }

    // ---- input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        commitRename();
        if (tab != Tab.URL && list.scroll(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (renameBox != null && !renameBox.isMouseOver(event.x(), event.y())) commitRename();
        if (super.mouseClicked(event, doubleClick)) return true;
        return tab != Tab.URL && list.click(event.x(), event.y());
    }

    /**
     * Enter keeps a new favorite name and Escape drops it; the inventory key closes the screen like a container unless
     * the player is typing into a text box.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (renameBox != null && getFocused() == renameBox) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                commitRename();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                closeRename();
                return true;
            }
        }
        if (!(getFocused() instanceof EditBox) && minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        // a drag that ended just before closing still counts
        if (volumeToSend >= 0) ClientPlayNetworking.send(new Packets.SetVolume(pos, volumeToSend));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The radio's own volume, 0 to 100 %, for everyone who hears it. */
    private final class VolumeSlider extends KeySlider {
        VolumeSlider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, value);
            updateMessage();
        }

        void set(double v) {
            setValue(v);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("worldradio.volume", Math.round(value * 100)));
        }

        @Override
        protected void applyValue() {
            volumeValue = value;
            volumeToSend = (float) value;
        }
    }
}
