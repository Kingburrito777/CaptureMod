# CaptureMod

CaptureMod is a Minecraft mod designed to capture detailed screenshots and 3D block data from Litematica schematic files in-game. It automates the process of taking multi-angle screenshots (orthogonal and radial views) and performs raycasting to map 2D pixel coordinates to 3D block coordinates and block states. This mod is particularly useful for generating datasets for machine learning tasks, such as 3D voxel modeling, segmentation, and generative AI in Minecraft.

## Features

- **Automated Screenshot Capture**: Captures screenshots from multiple perspectives (front, back, left, right, top, and radial views) of a loaded Litematica schematic.
- **3D Block Data Mapping**: Performs raycasting every 4th pixel to record 3D coordinates and block states, creating a 2D-to-3D mapping of the schematic.
- **Environment Control**: Sets up consistent rendering conditions (noon, clear weather, spectator mode, hidden HUD) for high-quality captures.
- **JSON Output**: Saves pixel data with coordinates and block states in JSON format for further processing.
- **Schematic Management**: Loads and places Litematica schematics, with cleanup to ensure no residual blocks or entities remain.

## Example Output

Below is an example of a raw screenshot (left) and its corresponding pixel mapping data (right). The pixel mapping includes 3D coordinates and block states for each sampled pixel, enabling advanced analysis and dataset creation.

![Example Screenshot and Pixel Mapping](example_screenshot_mapping.png)

## Installation

1. **Prerequisites**:
    - Minecraft Forge or Fabric for the target Minecraft version.
    - Litematica mod installed.
    - IntelliJ with the Minecraft Mod Maker extension for development.

2. **Steps**:
    - Clone the repository: `git clone https://github.com/Kingburrito777/CaptureMod`
    - Open the project in IntelliJ and build the mod using the Minecraft Mod Maker extension.
    - Place the compiled `.jar` file in your Minecraft `mods` folder.
    - Ensure the `schematics` folder contains valid `.litematic` files in your Minecraft directory.

## Usage

1. Launch Minecraft with the mod installed.
2. Load a world and ensure a Litematica schematic (e.g., `24150.litematic`) is in the `schematics` folder.
3. Press the default capture key (`P`) to start the capture process.
4. The mod will:
    - Place the schematic at (0, 0, 0).
    - Set up the environment (spectator mode, noon, clear weather).
    - Capture screenshots and pixel data from multiple angles.
    - Save screenshots to the `screenshots` folder and JSON data to the `captures/<schematic_name>` folder.
5. After completion, the schematic is removed, and the environment is reset.

## Output Format

The mod generates:
- **Screenshots**: PNG files named `<schematic_name>_<view>.png` (e.g., `24150_front.png`).
- **JSON Data**: Files named `capture_<view>.json` containing pixel data in the format:
  ```json
  {
    "width": 1195,
    "height": 1086,
    "pixels": [
      {
        "x": 448,
        "y": 792,
        "coords": { "x": 12.688871, "y": 7.777778, "z": 10.406028 },
        "blockstate": "minecraft:water[level=1]"
      },
      ...
    ]
  }
  ```

## Development

- **Main Classes**:
    - `CapturemodClient`: Initializes the mod and registers the capture keybinding.
    - `SchematicCaptureManager`: Manages the capture process, including schematic loading, camera positioning, and environment setup.
    - `CaptureLogic`: Handles raycasting and pixel data generation.
    - `CameraPosition`: Defines camera positions for captures.

- **Building**:
    - Use IntelliJ with the Minecraft Mod Maker extension to build the mod.
    - Ensure dependencies (Forge/Fabric, Litematica) are configured in `build.gradle`.

- **Customization**:
    - Modify `DELAY_TICKS` in `SchematicCaptureManager` to adjust capture timing.
    - Adjust `calculateCameraPositionsRad` or `calculateCameraPositionsOrtho` for different camera angles.
    - Update the raycasting step size in `CaptureLogic` (default: every 8th pixel) for denser or sparser data.

## Use Cases

- **Dataset Creation**: Generate 2D-to-3D mappings for training generative AI models or 3D segmentation models in Minecraft.
- **Visual Analysis**: Use screenshots for visual language model (VLM) classification (e.g., identifying "castle" or "tower").
- **3D Reconstruction**: Leverage pixel mappings for image-to-3D voxel model training, similar to depth map prediction.
- **Research**: Explore interior segmentation or style transfer in Minecraft builds using the captured data.

## Limitations

- Requires Litematica mod and compatible schematic files.
- Raycasting may miss occluded blocks (e.g., interiors), limiting 3D data completeness.
- Performance depends on schematic size and system resources.
- Currently supports single-schematic captures; batch processing requires additional scripting.

## Contributing

Contributions are welcome! Please:
1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/???`).
3. Commit your changes (`git commit -m 'Add ???'`).
4. Push to the branch (`git push origin feature/???`).
5. Open a pull request.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.

## Contact

For questions or support, open an issue on GitHub or contact at [liamlarsen12@gmail.com].