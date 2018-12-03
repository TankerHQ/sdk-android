global Java_io_tanker_jni_JniLibKt_isKVMx86

CPUID_VERSION_INFORMATION equ   0x00000001
CPUID_HYPERVISOR_VENDOR equ     0x40000000

HYPERVISOR_PRESENT equ          0x80000000

Java_io_tanker_jni_JniLibKt_isKVMx86:
    push ebx

    ; Check if hypervisor is supported at all
    mov eax, CPUID_VERSION_INFORMATION
    cpuid
    and ecx, HYPERVISOR_PRESENT
    jz .not_kvm

    ; Check if the hypervisor is KVM
    mov eax, CPUID_HYPERVISOR_VENDOR
    cpuid
    cmp ebx, 'KVMK'
    jne .not_kvm
    cmp ecx, 'VMKV'
    jne .not_kvm
    cmp edx, 'M'
    jne .not_kvm

    ; We are, in fact, running under x86 KVM
    mov eax, 1
    pop ebx
    ret

.not_kvm:
    xor eax, eax
    pop ebx
    ret
