# OSMdroid Manager Pattern

## Problem
OSMdroid is a stateful, imperative Android View. Compose wants declarative MVVM.
Directly managing MapView markers/position in Compose led to:
- Lifecycle bugs (map resetting on navigation)
- State sync hell (OSMdroid internal state vs. ViewModel state)
- Untestable code

## Solution
`OsmdroidManager` acts as a **bridge**:

### Pattern: Callback-with-Return
1. **Manager** handles OSMdroid setup and Android View events
2. **Manager** calls callbacks for events (map move, long press, layout ready)
3. **ViewModel** processes business logic (creates data, saves to DB, BLE)
4. **ViewModel** returns processed data back to Manager
5. **Manager** applies data to OSMdroid imperatively

### Why This Works
- **ViewModel** stays pure business logic (no Android View dependencies)
- **Manager** encapsulates OSMdroid's imperative quirks
- **Screen composable** just wires callbacks (clean separation)

## Example Flow: Adding a Pin
1. User long-presses map → `OsmdroidManager.onMapLongPress(geoPoint)`
2. Calls `ViewModel.handleLongPress(geoPoint)` via callback
3. ViewModel:
    - Creates `PinData` with ID/timestamp
    - Updates `StateFlow<List<PinData>>`
    - Saves to Room database
    - Triggers BLE send
    - **Returns** the created `PinData`
4. Manager receives `PinData` back, adds Marker to MapView

## Key Decisions
- No StateFlow observation in Manager (callback-with-return instead)
- Manager doesn't know about DB/BLE/Repository
- ViewModel owns all business logic and side effects