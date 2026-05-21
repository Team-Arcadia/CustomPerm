# CustomPerm Server Setup Guide

Complete setup guide for installing CustomPerm, exposing commands, creating aliases, managing grades, and assigning permissions on a Minecraft server.

## Requirements

- Minecraft `1.21.1`
- NeoForge `21.1.221+`
- Java `21`
- CustomPerm installed in the server `mods/` folder
- Optional but recommended: LuckPerms `5.4.150+`

## Installation

1. Stop the server.
2. Place the CustomPerm jar in the server `mods/` folder.
3. Optional: place the LuckPerms NeoForge jar in the same folder.
4. Start the server once.

```text
mods/customperm-1.0.2.jar
mods/LuckPerms-NeoForge-5.4.x.jar
```

CustomPerm creates its config folder on first launch:

```text
config/arcadia/customperm/
```

## Key Concepts

### Exposed Commands

A command must be exposed before CustomPerm can grant it to non-op players.

```mcfunction
/customperm command add gamemode
```

This creates the permission node:

```text
customperm.command.gamemode
```

### Aliases

Aliases are custom commands that run one or more internal command steps.

```mcfunction
/customperm alias add up tp ~ ~100 ~
```

This creates:

```text
/up
customperm.alias.up
```

Important: for aliases, the player only needs the alias permission. For example, `customperm.alias.up` is enough to use `/up`; the player does not also need `customperm.command.tp`.

### Permission Nodes

| Node | Meaning |
|------|---------|
| `customperm.command.<command>` | Grants access to an exposed command. |
| `customperm.alias.<alias>` | Grants access to a CustomPerm alias. |
| `customperm.command.*` | Grants access to every exposed command. |
| `customperm.alias.*` | Grants access to every alias. |

Use wildcards carefully on public servers.

## Setup With LuckPerms

This is the recommended setup for public or production servers. LuckPerms handles groups, users, inheritance, contexts, and storage. CustomPerm exposes commands and checks LuckPerms permissions.

Note: this section shows the in-game command workflow. The same LuckPerms group, user, inheritance, and permission changes can also be made through the LuckPerms web editor.

### 1. Confirm LuckPerms Is Active

```mcfunction
/customperm status
```

Expected backend:

```text
backend=LuckPerms
```

If the backend is not LuckPerms, verify that LuckPerms is in the `mods/` folder, that its version is `5.4.150+`, and that the server was fully restarted.

### 2. Configure LuckPerms Fallback Mode

Open:

```text
config/arcadia/customperm/settings.json
```

Recommended for public servers:

```json
{
  "luckPermsFallbackMode": "deny"
}
```

Available modes:

```text
deny
internal
```

Recommended: use `deny` on production servers. If LuckPerms fails, permissions fail closed instead of falling back silently.

After editing:

```mcfunction
/customperm reload
```

### 3. Expose a Command

```mcfunction
/customperm command add gamemode
```

Permission node:

```text
customperm.command.gamemode
```

### 4. Create a LuckPerms Group

```mcfunction
/lp creategroup vip
```

### 5. Grant the Permission

```mcfunction
/lp group vip permission set customperm.command.gamemode true
```

### 6. Assign the Group

```mcfunction
/lp user Steve parent add vip
```

Now Steve can use `/gamemode` without being op.

### 7. Test the Permission

```mcfunction
/customperm test Steve customperm.command.gamemode
/lp user Steve permission check customperm.command.gamemode
```

Expected: `GRANTED` from CustomPerm and `true` from LuckPerms.

### 8. Debug a Command

```mcfunction
/customperm debug Steve gamemode
```

Check that the command exists, is exposed, the permission service says true, and the wrapper decision is true.

## Aliases With LuckPerms

Aliases are the safest way to grant narrow actions without exposing a full command.

### Allow Spectator Mode Only

```mcfunction
/customperm alias add spec gamemode spectator
/lp group vip permission set customperm.alias.spec true
```

Players use:

```mcfunction
/spec
```

They do not need `customperm.command.gamemode`.

### Create an /up Alias

```mcfunction
/customperm alias add up tp ~ ~100 ~
/lp group vip permission set customperm.alias.up true
```

Players use:

```mcfunction
/up
```

### Create a /pingtest Alias

```mcfunction
/customperm alias add pingtest say ping
/lp user Steve permission set customperm.alias.pingtest true
```

Players use:

```mcfunction
/pingtest
```

Expected result:

```text
[Server] ping
```

## preserveOriginalRequires

Some commands have their own internal permission checks. CustomPerm can either replace the original requirement or preserve it in addition to the CustomPerm permission.

Default state: commands default to `false`. If a command is not listed in `preserveOriginalRequires`, CustomPerm does not preserve the original Brigadier requirement once the command is exposed and the CustomPerm permission passes.

Configured in:

```text
config/arcadia/customperm/commands.json
```

Example:

```json
{
  "grantedCommands": ["gamemode", "time", "adminpanel"],
  "preserveOriginalRequires": {
    "gamemode": false,
    "time": false,
    "adminpanel": true
  }
}
```

Meaning:

| Command | Behavior |
|---------|----------|
| `gamemode: false` | CustomPerm permission is enough. |
| `time: false` | CustomPerm permission is enough. |
| `adminpanel: true` | CustomPerm permission and original command requirement are both required. |

Use `true` for sensitive mod commands that already have their own security model. Use `false` when CustomPerm should explicitly grant access.

After editing:

```mcfunction
/customperm reload
```

## Setup Without LuckPerms

If LuckPerms is not installed, CustomPerm uses its internal JSON grade system.

Internal grade commands are disabled when LuckPerms is active. If LuckPerms is installed, follow the [Setup With LuckPerms](#setup-with-luckperms) section instead.

### 1. Confirm Internal Backend

```mcfunction
/customperm status
```

Expected backend:

```text
backend=Internal
```

### 2. Create a Grade

```mcfunction
/customperm grade create vip
```

### 3. Expose a Command

```mcfunction
/customperm command add gamemode
```

### 4. Add Permission to the Grade

```mcfunction
/customperm grade addperm vip customperm.command.gamemode
```

### 5. Assign the Grade

```mcfunction
/customperm grade assign Steve vip
```

### 6. Test

```mcfunction
/customperm test Steve customperm.command.gamemode
```

Expected: `GRANTED`.

### Internal Staff Grade Example

```mcfunction
/customperm grade create staff
/customperm command add gamemode
/customperm command add time
/customperm command add weather
/customperm command add tp
/customperm grade addperm staff customperm.command.gamemode
/customperm grade addperm staff customperm.command.time
/customperm grade addperm staff customperm.command.weather
/customperm grade addperm staff customperm.command.tp
/customperm grade assign Steve staff
```

### Internal Alias Grade Example

```mcfunction
/customperm alias add spec gamemode spectator
/customperm alias add up tp ~ ~100 ~
/customperm alias add pingtest say ping
/customperm grade create helper
/customperm grade addperm helper customperm.alias.spec
/customperm grade addperm helper customperm.alias.up
/customperm grade addperm helper customperm.alias.pingtest
/customperm grade assign Steve helper
```

## Configuration Files

### commands.json

Stores exposed commands and command safety options.

```json
{
  "grantedCommands": ["gamemode", "time", "tp"],
  "preserveOriginalRequires": {
    "gamemode": false,
    "time": false,
    "tp": false
  }
}
```

### aliases.json

Stores aliases and macro steps.

```json
{
  "aliases": {
    "spec": ["gamemode spectator"],
    "up": ["tp ~ ~100 ~"],
    "pingtest": ["say ping"]
  }
}
```

The leading slash is optional in alias steps:

```json
"pingtest": ["say ping"]
"pingtest": ["/say ping"]
```

### grades.json

Used only when LuckPerms is not active.

```json
{
  "grades": {
    "vip": {
      "name": "vip",
      "permissions": [
        "customperm.command.gamemode",
        "customperm.alias.up"
      ]
    }
  },
  "userGrades": {
    "550e8400-e29b-41d4-a716-446655440000": ["vip"]
  }
}
```

When LuckPerms is active, this file is ignored.

### settings.json

Runtime safety settings.

Recommended:

```json
{
  "luckPermsFallbackMode": "deny"
}
```

## Security Notes

Aliases execute their internal steps with elevated authority. This is required so a non-op player can use a controlled alias like `/up` without direct access to `/tp`.

Every alias must be reviewed carefully.

Avoid aliases containing dangerous commands unless every player with access is fully trusted:

```text
op
deop
stop
ban
pardon
whitelist
reload
save-off
gamerule
data modify
```

Prefer narrow aliases.

Better:

```mcfunction
/customperm alias add spec gamemode spectator
```

Riskier:

```mcfunction
/customperm command add gamemode
```

`customperm.command.gamemode` grants access to every gamemode option.

## Useful Admin Commands

| Command | Purpose |
|---------|---------|
| `/customperm status` | Shows backend, exposed commands, aliases, and grades. |
| `/customperm reload` | Reloads config files from disk. |
| `/customperm test <player> <node>` | Checks whether a player has a permission node. |
| `/customperm debug <player> <command>` | Explains why a command is allowed or denied. |
| `/customperm scan [pattern]` | Lists commands in the dispatcher. Use a filter on heavily modded servers. |

## Common Workflows

### Give /gamemode to VIP With LuckPerms

```mcfunction
/customperm command add gamemode
/lp creategroup vip
/lp group vip permission set customperm.command.gamemode true
/lp user Steve parent add vip
```

### Give /gamemode to VIP Without LuckPerms

```mcfunction
/customperm command add gamemode
/customperm grade create vip
/customperm grade addperm vip customperm.command.gamemode
/customperm grade assign Steve vip
```

### Create /up With LuckPerms

```mcfunction
/customperm alias add up tp ~ ~100 ~
/lp group vip permission set customperm.alias.up true
```

### Create /up Without LuckPerms

```mcfunction
/customperm alias add up tp ~ ~100 ~
/customperm grade create vip
/customperm grade addperm vip customperm.alias.up
/customperm grade assign Steve vip
```

### Create /spec With LuckPerms

```mcfunction
/customperm alias add spec gamemode spectator
/lp group vip permission set customperm.alias.spec true
```

### Create /spec Without LuckPerms

```mcfunction
/customperm alias add spec gamemode spectator
/customperm grade create vip
/customperm grade addperm vip customperm.alias.spec
/customperm grade assign Steve vip
```

## Troubleshooting

### The Player Cannot See the Command

```mcfunction
/customperm reload
/lp user Steve permission check customperm.command.gamemode
/lp user Steve permission check customperm.alias.up
```

The player may need to reconnect if the client command tree did not refresh.

### The Command Appears but Fails

```mcfunction
/customperm debug Steve gamemode
```

Check whether the command exists, is exposed, is allowed by CustomPerm, or blocked by `preserveOriginalRequires`.

### An Alias Appears but Fails Internally

```mcfunction
/customperm alias steps up
/customperm test Steve customperm.alias.up
```

Expected alias step for `/up`:

```text
#0: tp ~ ~100 ~
```

The player does not need direct permission for the internal command.

### LuckPerms Says True but CustomPerm Denies

```mcfunction
/lp user Steve permission info customperm.alias.up
/lp user Steve permission check customperm.alias.up
/customperm test Steve customperm.alias.up
```

Compare LuckPerms contexts with the server/world where the player is testing.

## Best Practices

- Use LuckPerms for production servers.
- Set `luckPermsFallbackMode` to `deny`.
- Prefer aliases for narrow actions.
- Avoid exposing dangerous commands like `op`, `stop`, `ban`, `whitelist`, and `reload`.
- Use explicit permission nodes instead of wildcards.
- Use `preserveOriginalRequires: true` for sensitive mod commands.
- Test permissions with a real non-op player before using them on a public server.
- Audit aliases regularly with `/customperm alias list` and `/customperm alias steps <alias>`.

## Recommended Production Default

```json
{
  "luckPermsFallbackMode": "deny"
}
```
