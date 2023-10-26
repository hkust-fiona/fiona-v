/* #include "fiona_instr.h" */
#include <stdio.h>
#include "rocc.h"
#include "fiona.h"
#pragma GCC optimize ("no-inline")


uint16_t src[32] __attribute__ ((aligned (64))) = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19 ,20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31 };
uint16_t dst[32] __attribute__ ((aligned (64)));


int main(int argc, char** argv) {
    printf("This is VLDST test. Compiled at %s\n", __TIME__);
    int vl = 17;
    SETVL(vl);
    printf("Setting vl = 17\n");
    VLD(1, src);
    printf("VLD: src -> v1\n");
    VST(1, dst);
    printf("VST: v1 -> dst\n");
    for(int i = 0; i < 32;i ++) {
        dst[i]++;
    }
    printf("Inc all elements in dst by 1\n");

    vl = 3;
    SETVL(vl);
    printf("Setting vl = 3\n");
    VLD(2, dst);
    printf("VLD: dst -> v2\n");

    VST(2, src);
    printf("VST: v2 -> src\n");

    for(int i = 0; i < 32; i++) {
        printf("%d ", src[i]);
    }
    printf("\n");
    for(int i = 0; i < 32; i++) {
        printf("%d ", dst[i]);
    }
    printf("\n");
    printf("Finished.\n");
    return 0;
}
