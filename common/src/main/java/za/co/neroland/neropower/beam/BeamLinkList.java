package za.co.neroland.neropower.beam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;

import za.co.neroland.neropower.data.SaveKeys;

/**
 * The outgoing links of a multi-target beam source (Beam Splitter, Phased Array Emitter): an ordered
 * list of {@link BeamTarget}s, each with a share in permille (the splitter's; the emitter keeps 0). World
 * data only: <b>no owner is recorded</b> (authorisation is checked at link time), so
 * nothing here is personal data.
 *
 * <p>Save format (additive keys, flat so the same code reads every MC line): {@code LinkCount}, then per
 * link {@code Link<i>X/Y/Z} (int), {@code Link<i>Dim} (string), {@code Link<i>Share} (int). A save
 * without {@code LinkCount} reads as no links. Shares are capped cumulatively on read, so a tampered
 * save can never allocate more than the whole.
 */
public final class BeamLinkList {

    /** One link: where it points, and its share (permille). */
    public record Link(BeamTarget target, int share) {
    }

    /** Key prefix of every link field; the client sync tag drops every key that starts with it. */
    public static final String PREFIX = "Link";
    public static final String COUNT = "LinkCount";

    private final List<Link> links = new ArrayList<>();

    public BeamLinkList() {
    }

    public int size() {
        return this.links.size();
    }

    public boolean isEmpty() {
        return this.links.isEmpty();
    }

    public Link get(int index) {
        return this.links.get(index);
    }

    public List<Link> view() {
        return Collections.unmodifiableList(this.links);
    }

    /** Whether {@code target} is already linked. */
    public boolean contains(BeamTarget target) {
        for (Link link : this.links) {
            if (link.target().equals(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Append {@code target} with share 0. Refused (false) when {@code max} links are already held or the
     * target is already linked.
     */
    public boolean add(BeamTarget target, int max) {
        if (this.links.size() >= Math.max(0, max) || contains(target)) {
            return false;
        }
        this.links.add(new Link(target, 0));
        return true;
    }

    /** Remove link {@code index}; a stale index does nothing. */
    public boolean remove(int index) {
        if (index < 0 || index >= this.links.size()) {
            return false;
        }
        this.links.remove(index);
        return true;
    }

    /** Remove the link pointing at {@code target}, if any. */
    public boolean remove(BeamTarget target) {
        return this.links.removeIf(link -> link.target().equals(target));
    }

    /** Drop links beyond {@code max} (the config was lowered). */
    public void trim(int max) {
        while (this.links.size() > Math.max(0, max)) {
            this.links.remove(this.links.size() - 1);
        }
    }

    /** Sum of every share except link {@code except}. */
    public int sharesExcept(int except) {
        int sum = 0;
        for (int i = 0; i < this.links.size(); i++) {
            if (i != except) {
                sum += this.links.get(i).share();
            }
        }
        return sum;
    }

    /** Sum of every share. */
    public int totalShare() {
        return sharesExcept(-1);
    }

    /**
     * Set link {@code index}'s share, capped at 1000 minus the other shares ({@link SplitMath#capShare}).
     *
     * @return the share actually stored, or -1 for a stale index
     */
    public int setShare(int index, int permille) {
        if (index < 0 || index >= this.links.size()) {
            return -1;
        }
        int share = SplitMath.capShare(permille, sharesExcept(index));
        Link old = this.links.get(index);
        this.links.set(index, new Link(old.target(), share));
        return share;
    }

    /** Move link {@code index} one place earlier ({@code up}) or later; false when it cannot move. */
    public boolean move(int index, boolean up) {
        int other = up ? index - 1 : index + 1;
        if (index < 0 || index >= this.links.size() || other < 0 || other >= this.links.size()) {
            return false;
        }
        Collections.swap(this.links, index, other);
        return true;
    }

    /** The shares in list order. */
    public int[] shares() {
        int[] out = new int[this.links.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = this.links.get(i).share();
        }
        return out;
    }

    // --- persistence ---------------------------------------------------------------------------

    public void write(SaveKeys.Writer out) {
        out.putInt(COUNT, this.links.size());
        for (int i = 0; i < this.links.size(); i++) {
            Link link = this.links.get(i);
            BlockPos pos = link.target().pos();
            out.putInt(PREFIX + i + "X", pos.getX());
            out.putInt(PREFIX + i + "Y", pos.getY());
            out.putInt(PREFIX + i + "Z", pos.getZ());
            out.putString(PREFIX + i + "Dim", link.target().dimension());
            out.putInt(PREFIX + i + "Share", link.share());
        }
    }

    /** Replace the list with what {@link #write} stored; at most {@code max} links, shares capped. */
    public void read(SaveKeys.Reader in, int max) {
        this.links.clear();
        int count = Math.max(0, Math.min(Math.max(0, max), in.getInt(COUNT, 0)));
        int used = 0;
        for (int i = 0; i < count; i++) {
            String dim = in.getString(PREFIX + i + "Dim", "");
            if (dim.isEmpty()) {
                continue;
            }
            BeamTarget target = BeamTarget.of(new BlockPos(in.getInt(PREFIX + i + "X", 0),
                    in.getInt(PREFIX + i + "Y", 0), in.getInt(PREFIX + i + "Z", 0)), dim);
            if (contains(target)) {
                continue;
            }
            int share = SplitMath.capShare(in.getInt(PREFIX + i + "Share", 0), used);
            used += share;
            this.links.add(new Link(target, share));
        }
    }
}
