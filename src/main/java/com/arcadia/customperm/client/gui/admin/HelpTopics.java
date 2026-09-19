/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.admin;

import java.util.List;

/**
 * What the Help page says. One topic per feature, each written to be read alone: what it does, when to use it,
 * how, and the commands behind the page. A line starting with {@code /} is shown as a command; {@code - } starts a
 * point; {@code # } a heading inside a topic.
 */
final class HelpTopics {

    record Topic(String title, List<String> lines) {}

    private HelpTopics() {}

    static final List<Topic> ALL = List.of(
            new Topic("Getting started", List.of(
                    "CustomPerm decides who may run which command, without making them operator. It works alone, "
                            + "with its own grades, or beside LuckPerms, which then decides and CustomPerm reads it.",
                    "# The usual first steps",
                    "- Commands page: expose a command, such as /gamemode. An exposed command asks for the node "
                            + "customperm.command.gamemode instead of operator rights.",
                    "- Grades page: create a grade, give it that node, assign players to it.",
                    "- Rate limits page: optionally cap how often a player may use it.",
                    "The Dashboard shows which backend decides, the counts, and any alert. Every page has the same "
                            + "text commands behind it, listed at the end of each topic here.",
                    "/customperm status",
                    "/customperm reload")),
            new Topic("Who can administer", List.of(
                    "Administering needs op level 2 and the node customperm.admin. Changing an area needs its own "
                            + "node too: customperm.manage.commands, .aliases, .ratelimits, .grades, .logs, .config or "
                            + ".luckperms (customperm.* for all). Operator alone opens nothing, so a player made "
                            + "operator by mistake gets no access.",
                    "The console always has access. In singleplayer or LAN, the host has access without a node.",
                    "A page you can read but not change says Read-only and names the node it needs.",
                    "/customperm grade create admins",
                    "/customperm grade addperm admins customperm.*",
                    "/customperm grade assign <player> admins")),
            new Topic("Exposed commands", List.of(
                    "An exposed command stops asking for operator rights and asks for customperm.command.<name> "
                            + "instead. Anyone holding that node can use it; nobody else sees it.",
                    "Preserve original: the node AND the command's own requirement are both needed. Useful to narrow "
                            + "a command rather than open it: among operators, only those holding the node may use it.",
                    "Gate all (internal grades only): every command reads its node, not only exposed ones. Then a "
                            + "denied node closes any command, operators included, and a granted one opens it. Take "
                            + "care: a grade holding * or customperm.command.* gets every command.",
                    "/customperm command add <name>",
                    "/customperm command preserve <name> <true|false>",
                    "/customperm command gateall <true|false>",
                    "/customperm command remove <name>")),
            new Topic("Aliases", List.of(
                    "An alias is a new command running one or more commands in order, such as /heal running two "
                            + "effect commands. It is used by players holding customperm.alias.<name>.",
                    "Steps run at op level 4 as the player: anything a step can do, the alias can do. Review each "
                            + "step as carefully as granting that command itself.",
                    "Selectors such as @s and @p mean the player running the alias. A failing step is logged and "
                            + "the next steps still run, as in a command block. An alias calling itself stops at "
                            + "depth 8.",
                    "/customperm alias add <name> <cmd1; cmd2; ...>",
                    "/customperm alias addstep <name> <cmd>",
                    "/customperm alias steps <name>",
                    "/customperm alias remove <name>")),
            new Topic("Rate limits", List.of(
                    "A rate limit caps how many times one player may use an exposed command or an alias within a "
                            + "sliding window: 3 uses per 3600 seconds, for instance. Operators are limited too; the console is not.",
                    "Disabling a rule keeps its numbers, to enable it again as it was. The usage history survives "
                            + "restarts; Persistence says when it is written: with the world save (no cost per use) "
                            + "or after every use (nothing lost on a crash).",
                    "# In a cluster",
                    "Each rule has a scope: server, each server counting its own uses (the default); network, one "
                            + "budget for every server; or server names such as hub,survival, which share one budget "
                            + "while the others count alone. Outside a cluster every server counts its own.",
                    "/customperm ratelimit set <name> <max> <windowSeconds>",
                    "/customperm ratelimit scope <name> <server|network|hub,survival>",
                    "/customperm ratelimit persistence <name> <world_save|immediate>",
                    "/customperm ratelimit list")),
            new Topic("Grades", List.of(
                    "A grade is a set of nodes given to players: allowed nodes, and denied ones. A player can hold "
                            + "several grades. Used when LuckPerms is not installed; with LuckPerms, groups do this "
                            + "job and the Grades page is read-only.",
                    "Nodes can be exact (customperm.command.home) or wildcards (customperm.command.*, *).",
                    "Weight breaks a tie between two grades at the same level: the heavier one decides.",
                    "Display name: what listings show instead of the id, such as Very Important (vip).",
                    "/customperm grade create <name>",
                    "/customperm grade addperm <grade> <node>",
                    "/customperm grade adddeny <grade> <node>",
                    "/customperm grade assign <player> <grade>",
                    "/customperm grade weight <grade> <weight>")),
            new Topic("How a permission is decided", List.of(
                    "For one node, every entry the player reaches is compared:",
                    "- The most specific wins: the exact node beats a.b.*, which beats a.*, which beats *.",
                    "- At the same level, the player's own entry beats any grade; then the heaviest grade; then an "
                            + "entry limited to a context beats one that is not; then DENY beats ALLOW.",
                    "- A denied node applies to operators too. A node nobody sets leaves the command's own rule "
                            + "(usually operator only).",
                    "To check a result instead of guessing:",
                    "/customperm test <player> <node>",
                    "/customperm debug <player> <command>")),
            new Topic("Inheritance and refusals", List.of(
                    "A grade can inherit other grades: what a parent says applies where the grade says nothing as "
                            + "precise. A chain answers at the weight of the grade the player holds. Cycles are "
                            + "refused when they are made.",
                    "A grade can also refuse another: nothing it inherits brings that grade back. A player can refuse "
                            + "a grade too, wherever one of their grades would bring it, the default grade included. "
                            + "Refusing takes a grade out; it never turns what that grade allows into a denial.",
                    "/customperm grade parent add <grade> <parent>",
                    "/customperm grade parent adddeny <grade> <parent>",
                    "/customperm user denygrade <player> <grade>")),
            new Topic("Players", List.of(
                    "The Players page shows each player's grades, refusals and own nodes. A node given to one player "
                            + "wins over their grades at the same level, whatever a grade weighs: the exception one "
                            + "player needs, without inventing a grade for them.",
                    "Players are found among those online or who joined before; a name that never joined cannot be "
                            + "given anything in advance.",
                    "/customperm user addperm <player> <node>",
                    "/customperm user adddeny <player> <node>",
                    "/customperm user list <player>")),
            new Topic("Temporary entries", List.of(
                    "Nodes, grades held, parents, refusals, prefixes and meta can last a set time: add a duration "
                            + "such as 30m, 12h or 30d. An expired entry stops counting at once, and is then removed "
                            + "and written to the log.",
                    "Pages and listings show the time left.",
                    "/customperm grade assign <player> <grade> 30d",
                    "/customperm grade addperm <grade> <node> 12h")),
            new Topic("Contexts", List.of(
                    "An entry can apply in one context only: world=the_nether, gamemode=creative, a static context "
                            + "of this server such as region=eu, or server=<name> in a cluster. Several keys join with "
                            + "a comma and must all hold; two values of one key mean either.",
                    "An entry limited to a context outranks the same holder's entry without one. The command tree "
                            + "follows the player through portals and game mode changes.",
                    "A key nothing on this server sets is refused, since the entry would apply nowhere.",
                    "/customperm grade adddeny <grade> <node> world=the_nether",
                    "/customperm contexts set <key> <value>",
                    "/customperm contexts <player>")),
            new Topic("Default grade", List.of(
                    "A default grade applies to every player, below their own grades, like LuckPerms' default group. "
                            + "It is what restricts a player made operator by mistake, who holds no grade of their own.",
                    "Set it with the Default button on the Grades page.",
                    "/customperm grade setdefault <grade>",
                    "/customperm grade cleardefault")),
            new Topic("Tracks", List.of(
                    "A track is an ordered ladder of grades, lowest first, such as member, vip, moderator. Promote "
                            + "moves a player one rung up, demote one rung down; a track grants nothing by itself.",
                    "customperm.track.<track> lets a moderator move players along that track only. Moving oneself "
                            + "needs customperm.manage.grades. With a world typed on the Tracks tab, promote and "
                            + "demote read and move the grades held in that world only.",
                    "/customperm track create <track>",
                    "/customperm track append <track> <grade>",
                    "/customperm track promote <player> <track>",
                    "/customperm track demote <player> <track>")),
            new Topic("Meta", List.of(
                    "Meta is a key and a value on a grade or a player, such as homes = 5. Other mods read it when "
                            + "they ask NeoForge for a number or a text node. A player's own value wins, then the "
                            + "heaviest grade's.",
                    "/customperm grade meta <grade> set <key> <value>",
                    "/customperm user meta <player> set <key> <value>")),
            new Topic("Chat prefixes and names", List.of(
                    "Grades and players carry prefixes and suffixes, each with a priority. The highest priority a "
                            + "player reaches shows; Stacked shows several in a row. & colour codes are allowed.",
                    "Nothing shows until names are decorated (Names button on the Chat tab). The name is decorated, "
                            + "never the message, so chat stays signed; the prefix then shows everywhere the name "
                            + "does: chat, death messages, the tab list.",
                    "/customperm grade prefix <grade> add <priority> <text>",
                    "/customperm names on",
                    "/customperm names stack both stacked 3")),
            new Topic("Nicknames", List.of(
                    "A nickname replaces a player's name where names are shown, & codes allowed. One that reads as "
                            + "another player's name or nickname is refused.",
                    "Players holding customperm.nick may set their own with /nick; customperm.nick.color also allows "
                            + "colours.",
                    "/customperm user nick <player> set <nickname>",
                    "/nick <nickname>")),
            new Topic("Other mods", List.of(
                    "Mods that check their permissions through NeoForge are answered from the grades: giving "
                            + "somemod.feature to a grade works like any node, wildcards included.",
                    "Mods that call LuckPerms by name need LuckPerms itself. Modcheck lists them.",
                    "/customperm modcheck")),
            new Topic("LuckPerms", List.of(
                    "With LuckPerms installed, LuckPerms decides every permission and CustomPerm reads it: grant the "
                            + "nodes with /lp, or the LuckPerms page, which edits groups, players and tracks in game.",
                    "Exposed commands, aliases and rate limits keep working. The Grades and Players pages become "
                            + "read-only: what they hold waits and takes over if LuckPerms is removed.",
                    "If LuckPerms fails, luckPermsFallbackMode in settings.json decides: deny (refuse everything, "
                            + "the default) or internal (use the grades).")),
            new Topic("Import and export", List.of(
                    "Import brings LuckPerms groups, players and nodes over as grades; export writes the grades into "
                            + "LuckPerms. Both run in two steps: a preview that changes nothing and lists what would "
                            + "be carried and what left behind, then the change itself.",
                    "Add keeps what is already there; Replace empties it first. Import copies every config file to "
                            + "backup/ first. Export cannot be undone from here: run /lp export <file> before.",
                    "/customperm import preview",
                    "/customperm import confirm",
                    "/customperm export preview",
                    "/customperm export confirm")),
            new Topic("Cluster mode", List.of(
                    "Several servers without LuckPerms share their grades, exposed commands, aliases, rate limits "
                            + "and activity log through one MySQL or MariaDB database. A change on one server "
                            + "applies on the others within about two seconds.",
                    "# Setting it up (settings.json, then a restart)",
                    "- \"cluster\": { \"enabled\": true } on every server.",
                    "- connection \"direct\": fill serverName (different on each server) and database (host, port, "
                            + "name, user, password, tls). Or connection \"arcadia\" to go through Arcadia Lib.",
                    "- share: turn off any part a server should keep to itself.",
                    "# How it behaves",
                    "Two servers changing the same grade at once: the second is refused and shown the first change. "
                            + "Database unreachable: the server keeps the rights it last read and refuses changes "
                            + "until it is back. The Dashboard shows the cluster and the other servers heard.",
                    "server=<name> becomes a context, and each rate limit chooses who shares its budget.")),
            new Topic("Activity log", List.of(
                    "Every admin change is recorded, with what the admin saw. Player commands are recorded too when "
                            + "turned on; arguments of private commands such as /msg are masked by default.",
                    "Files are kept per day in <world>/customperm/logs, 30 days by default. In a cluster, entries "
                            + "from other servers show with @<server>.",
                    "/customperm log admin [count]",
                    "/customperm log players [count]",
                    "/customperm log record <true|false>")),
            new Topic("Alerts and troubleshooting", List.of(
                    "A red alert in chat and on the Dashboard means something needs an admin: a config file that "
                            + "failed to load (changes are then kept in memory but not saved), LuckPerms unavailable, "
                            + "or cluster mode unable to run. Each alert says what happened and what to do.",
                    "# A player cannot use a command",
                    "- Is it exposed (Commands page), or an alias?",
                    "- Does the player hold the node? /customperm test tells, and why.",
                    "- Does a rate limit refuse it? The player is told how long to wait.",
                    "/customperm debug <player> <command>",
                    "/customperm scan [pattern]"))
    );
}
