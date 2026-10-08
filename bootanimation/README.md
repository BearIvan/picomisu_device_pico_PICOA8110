# Picomisu boot logo

Approved tiramisu variant: coffee/cocoa coat, mascarpone inserts and biscuit accents.
The pink/turquoise original remains in `outputs/picomisu-variants/original`.

`picomisu.svg` is the vector master. `frame.png` is an opaque black 320 × 400
frame. `bootanimation.zip` uses ZIP_STORED and the factory two-part layout
(one initial frame, then the same frame looping until boot completes, 15 fps).
BootAnimation draws it at both factory eye centres for INNOLUX5K/SHARP5K.

`device.mk` installs this ZIP as `/system/media/bootanimation_sharp5k.zip`.
The Source assembler keeps this Source file instead of carrying the factory
animation once the system image is rebuilt. The factory files remain unchanged.

For the already installed Source 2.18, the same ZIP is installed at
`/data/misc/cusanim/picomisu-bootanimation.zip`, mode 0644, root:root,
SELinux `u:object_r:data_misc_pxr_file:s0`, selected by the existing
`persist.sys.customanim.boot` property. No firmware or partition was flashed.
The previous property was empty and that directory had no animation files.

To return this headset to its system default, open `adb shell` and clear only
that property in the device shell:

```sh
setprop persist.sys.customanim.boot ""
```

A reboot is needed to display the default again. The original system ZIP is
also backed up under `outputs/picomisu-bootanimation/backup`.

Installation verified on 2026-10-03: the headset returned to Android after reboot,
`sys.boot_completed=1`, `init.svc.bootanim=stopped`, the custom path persisted,
and the ZIP SHA-256 remained
`ddbc6a70e322b0649aaff4aaee708bc08bd51b294b04fa64a948d482e21a8fd9`.
The user confirmed seeing PICO first, then Picomisu. The first PICO screen belongs
to the early boot stage and is separate from this Android boot animation.
The next system-image build has not been run for this asset-only change.

To rebuild the package from a rendered PNG:

```text
python pico4-pro/tools/package-bootanimation.py <frame.png> <bootanimation.zip>
```

The renderer used for this version is `outputs/picomisu-bootanimation/render-frame.cjs`.
