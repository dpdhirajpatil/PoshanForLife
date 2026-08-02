import Foundation

/// Success envelope returned by every 2xx backend response.
/// Mirrors `backend/.../dto/ApiResponse.java` and the Android `ApiResponse.kt`
/// — this contract is shared by the Angular web app too, so it must not drift.
struct APIResponse<T: Decodable>: Decodable {
    let success: Bool
    let data: T?
    let meta: PageMeta?
}

/// Pagination metadata, present on list endpoints only.
/// The backend declares these as non-null primitives, but they're optional here
/// so a future endpoint that omits one can't fail the whole decode.
struct PageMeta: Decodable, Equatable {
    let total: Int?
    let page: Int?
    let limit: Int?
}

/// For endpoints whose useful result is "it worked" — lets them go through the
/// same generic `request` path instead of needing a separate no-content method.
struct EmptyResponse: Decodable {}
