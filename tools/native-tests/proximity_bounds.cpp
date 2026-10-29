#include <algorithm>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include "suggest/core/layout/proximity_info.h"
// Minimal synthetic JNI table: the only non-null array is fixed all-zero grid data.
static const int COUNT = 10 * 4 * MAX_PROXIMITY_CHARS_SIZE;
static jsize JNICALL length(JNIEnv*, jarray) { return COUNT; }
static void JNICALL ints(JNIEnv*, jintArray, jsize, jsize count, jint* output) {
    std::fill(output, output + count, KEYCODE_SPACE);
}
int main(int argc, char** argv) {
    JNINativeInterface_ table{};
    table.GetArrayLength = length;
    table.GetIntArrayRegion = ints;
    JNIEnv env{&table};
    latinime::ProximityInfo proximity(&env, 100, 40, 10, 4, 10, 10,
        reinterpret_cast<jintArray>(1), 0, nullptr, nullptr, nullptr, nullptr,
        nullptr, nullptr, nullptr, nullptr);
    int x = argc > 1 ? std::atoi(argv[1]) : 0;
    int y = argc > 2 ? std::atoi(argv[2]) : 40;
    bool result = proximity.hasSpaceProximity(x, y);
    bool expected = x >= 0 && x < 100 && y >= 0 && y < 40;
    std::printf("synthetic x=%d y=%d result=%d expected=%d\n", x, y, result, expected);
    if (result != expected) return 2;
    const int primary = 'a';
    int output[MAX_PROXIMITY_CHARS_SIZE];
    std::fill(output, output + MAX_PROXIMITY_CHARS_SIZE, 999);
    std::vector<int> locale;
    proximity.initializeProximities(&primary, &x, &y, 1, output, &locale);
    if (output[0] != primary) return 3;
    if (!expected) {
        for (int i = 1; i < MAX_PROXIMITY_CHARS_SIZE; ++i) {
            if (output[i] != NOT_A_CODE_POINT) return 4;
        }
    } else if (output[1] != KEYCODE_SPACE) return 5;
    return 0;
}
