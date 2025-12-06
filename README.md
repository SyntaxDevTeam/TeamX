# TeamX

## Basic Access
Basic access to the `/team` command - `teamx.cmd.team`

### Premium Commands

You can use the permission for all premium commands by adding `teamx.cmd.team.premium`.
You can also add individual permissions for specific premium commands from the list below.

| Command | Permission | Description |
|---------|------------|-------------|
| `/team create <name> <description>` | `teamx.cmd.team.create` | Creates a team with the given name and description. |
| `/team rename <new_name>` | `teamx.cmd.team.rename` | Changes the team name. |
| `/team description <new_description>` | `teamx.cmd.team.description` | Changes the team description. |
| `/team format` | `teamx.cmd.team.format` | Opens a GUI for editing the team format. |
| `/team delete` | `teamx.cmd.team.delete` | Deletes the team (only the leader can delete their team). |
| `/team invite <player>` | `teamx.cmd.team.invite` | Sends a team invitation to the specified player (leaders and managers can send invitations). |
| `/team kick <player>` | `teamx.cmd.team.kick` | Removes a player from the team (leaders and managers can remove members). |

### Basic Commands

You can use the permission for all basic commands by adding `teamx.cmd.team.basic`. You can also add individual permissions for specific basic commands from the list below.

| Command | Permission | Description |
|---------|------------|-------------|
| `/team accept` | `teamx.cmd.team.accept` | Accepts a team invitation. |
| `/team deny` | `teamx.cmd.team.deny` | Declines a team invitation. |
| `/team leave` | `teamx.cmd.team.leave` | Leaves the team. |

### Administrative Commands

All administrative commands require the `teamx.cmd.team.admin` permission.

| Command | Permission | Description |
|---------|------------|-------------|
| `/team admin adduser <team_name> <player>` | `teamx.cmd.team.admin` | Adds a player to a team as an administrator. |
| `/team admin removeuser <team_name> <player>` | `teamx.cmd.team.admin` | Removes a player from a team as an administrator. |
| `/team admin setleader <team_name> <player>` | `teamx.cmd.team.admin` | Sets a player as the team leader. |
| `/team admin rename <team_name> <new_name>` | `teamx.cmd.team.admin` | Changes the team name. |
| `/team admin delete <team_name>` | `teamx.cmd.team.admin` | Deletes a team. |
| `/team help` | `teamx.cmd.team.help` | Displays a list of available commands. |