# Desktop local-file workflow

A desktop host supplies `DesktopFileDescriptor` values for files selected through its native picker and may batch them through `DesktopFolderInput`. The workflow validates non-empty host URI/path metadata, creates pages, and can generate debug JSON. It does not recursively read arbitrary filesystem paths itself and it does not package a desktop executable in MP8.
