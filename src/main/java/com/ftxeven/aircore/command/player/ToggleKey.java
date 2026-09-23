package com.ftxeven.aircore.command.player;

import com.ftxeven.aircore.model.PlayerProfile.Toggles;
import com.ftxeven.aircore.permission.Permissions;

import java.util.function.BiFunction;
import java.util.function.Predicate;

public enum ToggleKey {

    MSG("msgtoggle", "chat.toggle.msg",
            Permissions.Command.of("msgtoggle"), Permissions.Command.others("msgtoggle"),
            Toggles::msg,
            (t, v) -> new Toggles(v, t.socialSpy(), t.chat(), t.mention(), t.announce(),
                    t.pay(), t.payConfirm(), t.tp(), t.tpAutoAccept(), t.tpConfirm())),

    SOCIAL_SPY("socialspy", "chat.spy.toggle",
            Permissions.Command.of("socialspy"), Permissions.Command.others("socialspy"),
            Toggles::socialSpy,
            (t, v) -> new Toggles(t.msg(), v, t.chat(), t.mention(), t.announce(),
                    t.pay(), t.payConfirm(), t.tp(), t.tpAutoAccept(), t.tpConfirm())),

    CHAT("chattoggle", "chat.toggle.chat",
            Permissions.Command.of("chattoggle"), Permissions.Command.others("chattoggle"),
            Toggles::chat,
            (t, v) -> new Toggles(t.msg(), t.socialSpy(), v, t.mention(), t.announce(),
                    t.pay(), t.payConfirm(), t.tp(), t.tpAutoAccept(), t.tpConfirm())),

    MENTIONS("mentiontoggle", "chat.toggle.mentions",
            Permissions.Command.of("mentiontoggle"), Permissions.Command.others("mentiontoggle"),
            Toggles::mention,
            (t, v) -> new Toggles(t.msg(), t.socialSpy(), t.chat(), v, t.announce(),
                    t.pay(), t.payConfirm(), t.tp(), t.tpAutoAccept(), t.tpConfirm())),

    ANNOUNCEMENTS("announcetoggle", "chat.toggle.announcements",
            Permissions.Command.of("announcetoggle"), Permissions.Command.others("announcetoggle"),
            Toggles::announce,
            (t, v) -> new Toggles(t.msg(), t.socialSpy(), t.chat(), t.mention(), v,
                    t.pay(), t.payConfirm(), t.tp(), t.tpAutoAccept(), t.tpConfirm())),

    PAY("paytoggle", "economy.pay.toggle.send",
            Permissions.Command.of("paytoggle"), Permissions.Command.others("paytoggle"),
            Toggles::pay,
            (t, v) -> new Toggles(t.msg(), t.socialSpy(), t.chat(), t.mention(), t.announce(),
                    v, t.payConfirm(), t.tp(), t.tpAutoAccept(), t.tpConfirm())),

    PAY_CONFIRM("payconfirmtoggle", "economy.pay.toggle.confirm",
            Permissions.Command.of("payconfirmtoggle"), Permissions.Command.others("payconfirmtoggle"),
            Toggles::payConfirm,
            (t, v) -> new Toggles(t.msg(), t.socialSpy(), t.chat(), t.mention(), t.announce(),
                    t.pay(), v, t.tp(), t.tpAutoAccept(), t.tpConfirm())),

    TP("tptoggle", "teleport.toggle.request",
            Permissions.Command.of("tptoggle"), Permissions.Command.others("tptoggle"),
            Toggles::tp,
            (t, v) -> new Toggles(t.msg(), t.socialSpy(), t.chat(), t.mention(), t.announce(),
                    t.pay(), t.payConfirm(), v, t.tpAutoAccept(), t.tpConfirm())),

    TP_AUTO_ACCEPT("tpautoaccept", "teleport.toggle.autoaccept",
            Permissions.Command.of("tpautoaccept"), Permissions.Command.others("tpautoaccept"),
            Toggles::tpAutoAccept,
            (t, v) -> new Toggles(t.msg(), t.socialSpy(), t.chat(), t.mention(), t.announce(),
                    t.pay(), t.payConfirm(), t.tp(), v, t.tpConfirm())),

    TP_CONFIRM("tpconfirmtoggle", "teleport.toggle.confirm",
            Permissions.Command.of("tpconfirmtoggle"), Permissions.Command.others("tpconfirmtoggle"),
            Toggles::tpConfirm,
            (t, v) -> new Toggles(t.msg(), t.socialSpy(), t.chat(), t.mention(), t.announce(),
                    t.pay(), t.payConfirm(), t.tp(), t.tpAutoAccept(), v));

    private final String commandKey;
    private final String langBase;
    private final String permission;
    private final String othersPermission;
    private final Predicate<Toggles> getter;
    private final BiFunction<Toggles, Boolean, Toggles> wither;

    ToggleKey(String commandKey, String langBase, String permission, String othersPermission,
              Predicate<Toggles> getter, BiFunction<Toggles, Boolean, Toggles> wither) {
        this.commandKey = commandKey;
        this.langBase = langBase;
        this.permission = permission;
        this.othersPermission = othersPermission;
        this.getter = getter;
        this.wither = wither;
    }

    public String commandKey() { return commandKey; }
    public String langBase() { return langBase; }
    public String permission() { return permission; }
    public String othersPermission() { return othersPermission; }

    public boolean get(Toggles toggles) { return getter.test(toggles); }
    public Toggles with(Toggles toggles, boolean value) { return wither.apply(toggles, value); }
}