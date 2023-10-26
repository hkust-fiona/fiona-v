#ifndef __FIONA_H__
#define __FIONA_H__
#include "rocc.h"
#define VLD(vregnum, src) { \
    ROCC_INSTRUCTION_I_R_I(0, vregnum, src, 0,       8, 10); \
}

#define VST(vregnum, dst) { \
    ROCC_INSTRUCTION_I_R_I(0, 0,       dst, vregnum, 9, 10); \
}

#define VADD(vd, v1, v2) { \
    ROCC_INSTRUCTION_I_I_I(0, vd, v1, v2, 1);  \
}

#define VSUB(vd, v1, v2) { \
    ROCC_INSTRUCTION_I_I_I(0, vd, v1, v2, 2);  \
}

#define VSADD(vd, r1, v2) { \
    ROCC_INSTRUCTION_I_R_I(0, vd, r1, v2, 3, 10); \
}

#define VSSUB(vd, r1, v2) { \
    ROCC_INSTRUCTION_I_R_I(0, vd, r1, v2, 4, 10); \
}

#define VSMUL(vd, r1, v2) { \
    ROCC_INSTRUCTION_I_R_I(0, vd, r1, v2, 5, 10); \
}

#define VSDIV(vd, r1, v2) { \
    ROCC_INSTRUCTION_I_R_I(0, vd, r1, v2, 6, 10); \
}

#define VSHUFFLE(vd, v1, v2) { \
    ROCC_INSTRUCTION_I_I_I(0, vd, v1, v2, 10);  \
}

#define VMAX(rd, v1) { \
    ROCC_INSTRUCTION_R_I_I(0, rd, v1, 0, 11, 10); \
}

#define VMIN(rd, v1) { \
    ROCC_INSTRUCTION_R_I_I(0, rd, v1, 1, 11, 10); \
}

#define VTANH(vd, v1) { \
    ROCC_INSTRUCTION_I_I_I(0, vd, v1, 1, 15);  \
}

#define VSIGMOID(vd, v1) { \
    ROCC_INSTRUCTION_I_I_I(0, vd, v1, 2, 15);  \
}

#define VRELU(vd, r1, v2) { \
    ROCC_INSTRUCTION_I_R_I(0, vd, r1, v2, 15, 10); \
}

#define DOTP(rd, v1, v2) { \
    ROCC_INSTRUCTION_R_I_I(0, rd, v1, v2, 13, 10); \
}

#define MVM(vd, v1) { \
	ROCC_INSTRUCTION_I_I_I(0, vd, v1, 0, 14); \
}

#define SETVL(r1) { \
    ROCC_INSTRUCTION_I_R_I(0, 0, r1, 0, 12, 10); \
}

#define SETMASK(r1, r2) { \
	ROCC_INSTRUCTION_I_R_R(0, 1, r1, r2, 12, 11, 12); \
}

#define SETMAT(r1) { \
    ROCC_INSTRUCTION_I_R_I(0, 2, r1, 0, 12, 10); \
}

#endif
