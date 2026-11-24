# Item scripts

Every item can have a script that says what it does. Write it in the editor (**Project > Items**), start from one of
the examples there, or load a text file. Mistakes are shown, with their line, as you type.

A script is made of blocks. Each starts with `on` and the moment it runs:

| Block | When it runs | `user` | `target` |
|-------|--------------|--------|----------|
| `on use` | A consumable or key item is used | Whoever uses it | Who it's used on (nobody, for the whole party or all enemies) |
| `on hit` | The wearer's attack hits | The wearer | Who was hit |
| `on hurt` | The wearer is hit by an attack and survives | The wearer | The attacker |
| `on battle` | A battle starts | The wearer | The wearer |
| `on round` | Each round of a battle starts | The wearer | The wearer |

`on use` is for things that aren't worn; the others are for equipped weapons, armor and accessories.

## Commands

`WHO` is `user`, `target`, `party`, `enemies`, `random_enemy` or `random_ally`.

| Command | What it does |
|---------|--------------|
| `heal WHO 20` | Gives back HP |
| `mana WHO 10` | Gives back MP (a negative number takes it) |
| `damage WHO 8` | Takes HP, ignoring defense. Outside battle it leaves at least 1 |
| `revive WHO 15` | Brings back the fallen with this much HP. Nothing else affects the fallen |
| `poison WHO 3 for 4` | 3 damage at the end of each round, for 4 rounds (battle only) |
| `stun WHO 1` | Misses 1 turn (battle only) |
| `boost WHO attack 5 for 3` | Changes `attack`, `defense` or `speed` for 3 rounds; may be negative (battle only) |
| `cure WHO` | Ends poison and stun |
| `gold 50` | Gives the party gold |
| `say "Ouch, {target}!"` | Shows text. `{user}`, `{target}`, `{item}` and sums such as `{damage}` are filled in |
| `set x = 5` | Remembers a number until the end of the block |
| `if` ... `else` ... `end` | Chooses; `else` is optional |
| `repeat 3` ... `end` | Does something several times |
| `stop` | Ends the block |

Put each command, `else` and `end` on its own line. Everything after `#` is a comment.

## Numbers

Whole numbers, `+ - * / %`, parentheses, and:

- `user.` or `target.` and `hp`, `maxhp`, `mp`, `maxmp`, `attack`, `defense`, `speed`, `level`, `tier` (an enemy's, 1 for the lowest, 0 for none), `alive`, `poisoned`, `stunned`
- `damage` (of the hit that set off `on hit` or `on hurt`), `round`, `battle` (1 in battle, 0 outside)
- `random(1, 6)`, and `chance(30)`, which is true 30% of the time

Compare with `< <= > >= == !=`, and combine with `and`, `or`, `not`. Dividing by 0 gives 0.

## Examples

```
# A sword that drains life from the weak
on hit
  if target.hp < target.maxhp / 2
    heal user damage / 2
    say "{user} drains {target}!"
  end
```

```
# Spiked armor that braces at the start of a battle
on battle
  boost user defense 3 for 2
on hurt
  damage target 1 + damage / 4
```

Scripts can only do what's listed here: they can't touch files or the network. One that never ends is stopped after
2000 steps. Effects that a script causes don't set off other scripts, so thorns can't hurt thorns for ever.

In `items.txt`, a script is written on one line with `\n` between its lines.
