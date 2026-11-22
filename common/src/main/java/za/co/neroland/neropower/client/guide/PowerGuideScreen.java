package za.co.neroland.neropower.client.guide;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neropower.guide.PowerGuideData;

/**
 * The Power Guide viewer (client only): one step per page with its icon, title and text, and buttons to
 * page through steps and chapters. Opened from the Guide button on a NeroPower machine screen at the
 * chapter for that block; Back (or Escape) returns to that screen, whose menu stays open meanwhile.
 * It reads the guide's data files from the mod jar, keeps no state of its own and sends nothing.
 */
public final class PowerGuideScreen extends Screen {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 176;
    private static final int PAD = 10;
    private static final int BUTTON_H = 20;

    private static final int EDGE = 0xFF5A6470;
    private static final int PANEL = 0xF0161A20;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int TEXT_2 = 0xFFA8B0B8;
    private static final int ACCENT = 0xFFFFC44D;

    private static @Nullable PowerGuideData cached;

    private final @Nullable Screen parent;
    private final PowerGuideData guide;
    private int chapter;
    private int step;

    private int px;
    private int py;
    private @Nullable Button prevStep;
    private @Nullable Button nextStep;

    /**
     * @param parent   the screen Back returns to (or null to close)
     * @param itemId   a block or item id ({@code neropower:x}) whose chapter and step to open, or null for
     *                 the first chapter
     */
    public PowerGuideScreen(@Nullable Screen parent, @Nullable String itemId) {
        super(Component.translatable("gui.neropower.power_guide.title"));
        this.parent = parent;
        this.guide = guide();
        if (itemId != null) {
            int c = this.guide.chapterFor(itemId);
            if (c >= 0) {
                this.chapter = c;
                this.step = this.guide.chapters().get(c).stepFor(itemId);
            }
        }
    }

    /** The guide pages (read once from the jar; tolerant of missing files). */
    public static PowerGuideData guide() {
        PowerGuideData data = cached;
        if (data == null) {
            data = PowerGuideData.fromClasspath();
            cached = data;
        }
        return data;
    }

    @Override
    protected void init() {
        this.px = (this.width - PANEL_W) / 2;
        this.py = Math.max(4, (this.height - PANEL_H - BUTTON_H - 6) / 2);
        int by = this.py + PANEL_H + 4;
        int w = (PANEL_W - 4 * 4) / 5;
        int x = this.px;
        addRenderableWidget(Button.builder(Component.literal("<<"), b -> chapter(-1))
                .bounds(x, by, w, BUTTON_H).build());
        x += w + 4;
        this.prevStep = addRenderableWidget(Button.builder(Component.translatable("gui.neropower.power_guide.previous"),
                b -> step(-1)).bounds(x, by, w, BUTTON_H).build());
        x += w + 4;
        this.nextStep = addRenderableWidget(Button.builder(Component.translatable("gui.neropower.power_guide.next"),
                b -> step(1)).bounds(x, by, w, BUTTON_H).build());
        x += w + 4;
        addRenderableWidget(Button.builder(Component.literal(">>"), b -> chapter(1))
                .bounds(x, by, w, BUTTON_H).build());
        x += w + 4;
        addRenderableWidget(Button.builder(Component.translatable("gui.neropower.power_guide.back"), b -> onClose())
                .bounds(x, by, w, BUTTON_H).build());
        updateButtons();
    }

    private void chapter(int delta) {
        int n = this.guide.chapters().size();
        if (n == 0) {
            return;
        }
        this.chapter = Math.floorMod(this.chapter + delta, n);
        this.step = 0;
        updateButtons();
    }

    private void step(int delta) {
        List<PowerGuideData.Chapter> chapters = this.guide.chapters();
        if (chapters.isEmpty()) {
            return;
        }
        int n = chapters.get(this.chapter).steps().size();
        this.step = Math.max(0, Math.min(n - 1, this.step + delta));
        updateButtons();
    }

    private void updateButtons() {
        List<PowerGuideData.Chapter> chapters = this.guide.chapters();
        int n = chapters.isEmpty() ? 0 : chapters.get(this.chapter).steps().size();
        if (this.prevStep != null) {
            this.prevStep.active = this.step > 0;
        }
        if (this.nextStep != null) {
            this.nextStep.active = this.step < n - 1;
        }
    }

    private void draw(GuiGraphicsExtractor extractor) {
        int x = this.px;
        int y = this.py;
        extractor.fill(x - 1, y - 1, x + PANEL_W + 1, y + PANEL_H + 1, EDGE);
        extractor.fill(x, y, x + PANEL_W, y + PANEL_H, PANEL);
        extractor.text(this.font, this.title, x + PAD, y + 7, ACCENT, false);
        List<PowerGuideData.Chapter> chapters = this.guide.chapters();
        if (chapters.isEmpty()) {
            return;
        }
        PowerGuideData.Chapter c = chapters.get(this.chapter);
        PowerGuideData.Step s = c.steps().get(this.step);
        Component chapterLine = Component.translatable(c.titleKey());
        extractor.text(this.font, chapterLine, x + PANEL_W - PAD - this.font.width(chapterLine), y + 7, TEXT_2, false);
        extractor.fill(x + PAD, y + 18, x + PANEL_W - PAD, y + 19, EDGE);
        extractor.item(icon(s.icon()), x + PAD, y + 24);
        extractor.text(this.font, Component.translatable(s.titleKey()), x + PAD + 22, y + 28, TEXT, false);
        Component page = Component.translatable("gui.neropower.power_guide.page", this.step + 1, c.steps().size());
        extractor.text(this.font, page, x + PANEL_W - PAD - this.font.width(page), y + 28, TEXT_2, false);
        List<FormattedCharSequence> lines = this.font.split(Component.translatable(s.textKey()), PANEL_W - 2 * PAD);
        int ty = y + 46;
        int maxLines = (PANEL_H - 46 - 6) / 10;
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            extractor.text(this.font, lines.get(i), x + PAD, ty + 10 * i, TEXT, false);
        }
    }

    private static ItemStack icon(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) {
            return new ItemStack(Items.BOOK);
        }
        Item item = BuiltInRegistries.ITEM.getValue(key);
        return item == null || item == Items.AIR ? new ItemStack(Items.BOOK) : new ItemStack(item);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft == null) {
            return;
        }
        //? if >=26.2 {
        this.minecraft.gui.setScreen(this.parent);
        //?} else {
        /*this.minecraft.setScreen(this.parent);
        *///?}
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        draw(extractor);
    }
    //?} else {
    /*@Override
    public void render(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.render(extractor, mouseX, mouseY, partialTick);
        draw(extractor);
    }
    *///?}
}
