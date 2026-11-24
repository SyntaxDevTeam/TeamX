<div align="center">

# TeamX

**Build your clan. Grow stronger together. Become a force on your SMP.**

<p>
    <a href="#-download"><img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3.2.0/assets/cozy/supported/paper_vector.svg" alt="Supports Paper" height="56"></a>
    <a href="#-download"><img src="https://github.com/SyntaxDevTeam/PunisherX/raw/main/assets/badges/folia.svg" alt="Supports Folia" height="56"></a>
    <a href="#-download"><img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3.2.0/assets/cozy/supported/purpur_vector.svg" alt="Supports Purpur" height="56"></a>
</p>

[![Build](https://github.com/SyntaxDevTeam/TeamX/actions/workflows/buildexplorer.yml/badge.svg?branch=main)](https://github.com/SyntaxDevTeam/TeamX/actions/workflows/buildexplorer.yml) [![Issues](https://img.shields.io/github/issues/SyntaxDevTeam/TeamX)](https://github.com/SyntaxDevTeam/TeamX/issues) [![Last commit](https://img.shields.io/github/last-commit/SyntaxDevTeam/TeamX)](https://github.com/SyntaxDevTeam/TeamX/commits) [![Java 25](https://img.shields.io/badge/Java-25-orange)](https://github.com/SyntaxDevTeam/TeamX)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**[Download](#download)** · **[Quick start](#quick-start)** · **[Commands and permissions](#commands-and-permissions)** · **[Discord](https://discord.gg/Zk6mxv7eMh)**

</div>

TeamX gives Minecraft SMP players a place to form a clan, recruit members and build a shared identity. Start a small group of friends, a knightly order or a castle guard. Give it a name, write its story and choose how its name appears in game.

## What you can do

| Feature | What it adds to your server |
| --- | --- |
| **Clans and invitations** | Create a clan, send invitations and manage members. Staff can change leaders, move membership and help resolve ownership issues. |
| **Clan identity** | Customize the clan name, unique short tag, description and display format. Use PlaceholderAPI to show clan details in plugins that support its placeholders. |
| **Clan panel** | Open `/team` to browse clans, manage members and their specializations, and edit clan settings. |
| **Clan home and communication** | Set a shared base, send private messages and share your coordinates with clanmates. |
| **Plot lookup** | Run `/team plots` to see the plots you can access through PlotsX. |
| **Permission-based team power** | Use LuckPerms to give members power values that TeamX can expose through PlaceholderAPI. |
| **Name moderation** | With CleanerX installed, TeamX checks names when players create or rename clans. |
| **Simple setup** | Start with a local SQLite database. Configure MySQL, MariaDB or PostgreSQL when you need a separate database server. |

## Download

Open a run on `main` with a successful **Build and verify** job in **[GitHub Actions](https://github.com/SyntaxDevTeam/TeamX/actions/workflows/buildexplorer.yml)**. Download the **TeamX** artifact and extract `TeamX-<version>.jar`. GitHub requires you to sign in to download workflow artifacts.

TeamX is currently a development build. The clan panel, formatting editor and core clan commands have been smoke-tested locally on **Paper 1.21.10**, **Folia 1.21.8** and **Purpur 1.21.10**. Use the Java version required by your server. The plugin targets Java 21.

## Quick start

1. Place the TeamX JAR in your server's `plugins/` folder and start the server. The first startup downloads Kotlin and the other runtime libraries.
2. Keep SQLite for a local setup, or configure your database in `plugins/TeamX/config.yml`. Set `language` to `EN` or `PL` for the bundled translations.
3. Grant `teamx.cmd.team` for access to `/team`. Add `teamx.cmd.team.create` and `teamx.cmd.team.invite` to let players start and grow a clan. Grant `teamx.cmd.basic` to let players accept or decline invitations and leave their clan.
4. Try `/team create "Castle Guard" Wardens of the eastern gate`, then `/team invite Alex`. Alex can join with `/team accept`.

LuckPerms, PlaceholderAPI, PlotsX, CleanerX and Vault/VaultUnlocked are optional. Vault with an economy provider is required when a name or tag change has a positive fee. For runtime reloads on Paper, use a PlugManX version that supports Paper plugins. TeamX was smoke-tested with PlugManX 3.2.1.

Start with a backup before upgrading. The startup migration assigns unique tags to existing clans and reconciles the founder with the membership table while preserving clan IDs, names, descriptions and display formats. If you are upgrading from H2, transfer your data to a supported database before installing this build.

## Commands and permissions

All `/team` subcommands require `teamx.cmd.team` in addition to any permission shown below. Names containing spaces go in quotes.

| Task | Command | Additional permission |
| --- | --- | --- |
| Open the clan panel | `/team` | Access to `/team` |
| Create a clan | `/team create <name> [description]` | `teamx.cmd.team.create` |
| Change clan identity | `/team rename <name>`, `/team tag <tag>`, `/team description <text>`, `/team format` | Matching `teamx.cmd.team.<command>` node |
| Assign a rank | `/team rank <player> <manager\|elite\|member>` | `teamx.cmd.team.rank` and clan leadership |
| Assign or clear a specialization | `/team specialization <player> <assassin\|builder\|miner\|alchemist\|none>` | `teamx.cmd.team.specialization` and clan leadership |
| Set or remove the clan home | `/team sethome`, `/team delhome` | Matching `teamx.cmd.team.<command>` node and Founder or Master rank |
| Teleport or communicate | `/team home`, `/team ping`, `/team chat [message]` | `teamx.cmd.basic` or the matching `teamx.cmd.team.<command>` node |
| Invite or remove a member | `/team invite <player>`, `/team kick <player>` | `teamx.cmd.team.invite`, `teamx.cmd.team.kick` |
| Delete your clan | `/team delete` | `teamx.cmd.team.delete` and clan leadership |
| Respond to an invitation or leave | `/team accept`, `/team deny`, `/team leave` | `teamx.cmd.basic` or the matching `teamx.cmd.team.accept`, `teamx.cmd.team.deny`, `teamx.cmd.team.leave` node |
| Browse accessible plots | `/team plots [page]` | Access to `/team` and a running PlotsX instance |
| Open help | `/team help [page]` | `teamx.cmd.team.help` |
| Manage clans as staff | `/team admin <adduser\|removeuser\|setleader\|rename\|tag\|delete> …` | `teamx.cmd.team.admin` |
| Inspect or reload TeamX | `/teamx version`, `/teamx reload`, or the `/ltx` alias | `teamx.cmd.ltx` |

`teamx.cmd.team.creator` grants the management commands listed above. The Founder controls identity, formatting, roles, specializations and clan deletion. Masters can invite, remove Elites and Members, edit the description and manage the home. Elites and Members have the same management permissions. Staff can transfer leadership with `/team admin setleader`.

Tags are 1–8 ASCII letters or digits and are stored in uppercase. Clan names remain independent of tags. Configure fees under `team.fees.rename` and `team.fees.tag`. A paid change requires confirmation. The `teamx.fees.bypass` and `teamx.cmd.team.admin` permissions waive the fee.

Home teleportation uses `team.home.delay-seconds` and cancels on movement or damage during the wait. Enable `team.home.require-plotsx` to require an accessible PlotsX plot when saving a home. `/team chat` toggles private chat until logout, while `/team chat <message>` sends a single message. Coordinate sharing uses `team.ping.cooldown-seconds`. TeamX cancels the public chat event in clan chat mode and filters messages sent with `/team chat <message>` from the standard server command log. Chat bridges must respect event cancellation.

PlaceholderAPI exposes the tag as `%teamx_tag%`. The `{tag}` token is also available in `Placeholders.chat_format.team`.

## Build from source

Use JDK 21 and run:

```bash
./gradlew build
```

The installable JAR is written to `build/libs/TeamX-<version>.jar`.

## Support and development

Report a reproducible issue on **[GitHub](https://github.com/SyntaxDevTeam/TeamX/issues)** or ask for help on **[Discord](https://discord.gg/Zk6mxv7eMh)**. Include the TeamX version, server platform and version, and the steps to reproduce the issue.

The **[development roadmap (Polish)](docs/ROADMAP-PL.md)** tracks configurable clan roles, clan territories and performance work. TeamX is released under the [MIT License](LICENSE).
