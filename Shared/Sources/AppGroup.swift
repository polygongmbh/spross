import Foundation

/// The App-Group container the app, the widget and the watch share.
///
/// Which group that is belongs to the CONFIGURATION, not to this file: the two
/// Apple teams are separate accounts and a group identifier is registered to one
/// of them, so debug and release claim different ones. `project.yml` owns the
/// pair as `APP_GROUP_ID` and the generated entitlements read it from there;
/// these literals only have to answer with the same string Swift-side.
enum AppGroup {
    #if DEBUG
    static let identifier = "group.net.spross.app"
    #else
    static let identifier = "group.net.spross.data"
    #endif
}
