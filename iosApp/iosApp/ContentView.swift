import SwiftUI
import shared

struct ContentView: View {
    @StateObject private var viewModel = PlayerViewModelWrapper()
    @State private var searchText = ""
    @State private var selectedTab = 0
    
    var body: some View {
        TabView(selection: $selectedTab) {
            // Home Tab
            NavigationView {
                HomeView(viewModel: viewModel)
                    .navigationTitle("Audioly")
            }
            .tabItem {
                Image(systemName: "house.fill")
                Text("Home")
            }
            .tag(0)
            
            // Search Tab
            NavigationView {
                SearchView(viewModel: viewModel)
                    .navigationTitle("Search")
            }
            .tabItem {
                Image(systemName: "magnifyingglass")
                Text("Search")
            }
            .tag(1)
            
            // Library Tab
            NavigationView {
                LibraryView()
                    .navigationTitle("Library")
            }
            .tabItem {
                Image(systemName: "books.vertical.fill")
                Text("Library")
            }
            .tag(2)
            
            // Settings Tab
            NavigationView {
                SettingsView()
                    .navigationTitle("Settings")
            }
            .tabItem {
                Image(systemName: "gearshape.fill")
                Text("Settings")
            }
            .tag(3)
        }
        .overlay(alignment: .bottom) {
            if viewModel.isPlaying || viewModel.hasTrack {
                MiniPlayerView(viewModel: viewModel)
                    .padding(.bottom, 49) // Tab bar height
            }
        }
    }
}

// MARK: - Player ViewModel Wrapper (bridges KMP shared → SwiftUI)

class PlayerViewModelWrapper: ObservableObject {
    private let playerRepository: PlayerRepository
    private let streamExtractor: IosStreamExtractor
    
    @Published var isPlaying: Bool = false
    @Published var hasTrack: Bool = false
    @Published var title: String = ""
    @Published var uploader: String = ""
    @Published var thumbnailUrl: String = ""
    @Published var positionMs: Int64 = 0
    @Published var durationMs: Int64 = 0
    @Published var isBuffering: Bool = false
    
    init() {
        self.playerRepository = PlayerRepository(mainDispatcher: DispatchersKt.Main.immediate)
        self.streamExtractor = IosStreamExtractor()
        
        let player = IosAudioPlayer()
        playerRepository.attach(player: player)
    }
    
    func search(query: String) async -> [SearchResult] {
        // Bridge to KMP coroutine
        return [] // TODO: Implement async bridge
    }
    
    func play() {
        playerRepository.play()
    }
    
    func pause() {
        playerRepository.pause()
    }
    
    func togglePlayPause() {
        playerRepository.togglePlayPause()
    }
}

// MARK: - Placeholder Views

struct HomeView: View {
    @ObservedObject var viewModel: PlayerViewModelWrapper
    @State private var urlText = ""
    
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "music.note")
                .font(.system(size: 60))
                .foregroundColor(.accentColor)
            
            Text("Audioly")
                .font(.largeTitle)
                .fontWeight(.bold)
            
            Text("YouTube Audio Player")
                .font(.subheadline)
                .foregroundColor(.secondary)
            
            VStack(spacing: 12) {
                TextField("Paste YouTube URL...", text: $urlText)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                
                Button(action: {
                    // TODO: Extract and play
                }) {
                    Label("Play", systemImage: "play.fill")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .disabled(urlText.isEmpty)
            }
            .padding(.horizontal)
            
            Spacer()
        }
        .padding(.top, 40)
    }
}

struct SearchView: View {
    @ObservedObject var viewModel: PlayerViewModelWrapper
    @State private var searchText = ""
    
    var body: some View {
        VStack {
            Text("Search YouTube")
                .foregroundColor(.secondary)
        }
        .searchable(text: $searchText, prompt: "Search songs, artists...")
    }
}

struct LibraryView: View {
    var body: some View {
        VStack {
            Image(systemName: "music.note.list")
                .font(.system(size: 40))
                .foregroundColor(.secondary)
            Text("Your library will appear here")
                .foregroundColor(.secondary)
        }
    }
}

struct SettingsView: View {
    var body: some View {
        List {
            Section("Playback") {
                Text("Playback Speed: 1.0x")
                Text("Skip Interval: 15s")
            }
            Section("Subtitles") {
                Text("Language: Auto")
                Text("Font Size: 16pt")
            }
            Section("Cache") {
                Text("Cache Size: 512 MB")
            }
            Section("About") {
                Text("Audioly v1.0.0")
                Text("Built with Kotlin Multiplatform")
            }
        }
    }
}

struct MiniPlayerView: View {
    @ObservedObject var viewModel: PlayerViewModelWrapper
    
    var body: some View {
        HStack(spacing: 12) {
            // Thumbnail placeholder
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.gray.opacity(0.3))
                .frame(width: 48, height: 48)
                .overlay(
                    Image(systemName: "music.note")
                        .foregroundColor(.gray)
                )
            
            VStack(alignment: .leading, spacing: 2) {
                Text(viewModel.title.isEmpty ? "Not playing" : viewModel.title)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .lineLimit(1)
                Text(viewModel.uploader.isEmpty ? "" : viewModel.uploader)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }
            
            Spacer()
            
            Button(action: { viewModel.togglePlayPause() }) {
                Image(systemName: viewModel.isPlaying ? "pause.fill" : "play.fill")
                    .font(.title2)
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
        .background(.ultraThinMaterial)
    }
}

#Preview {
    ContentView()
}
