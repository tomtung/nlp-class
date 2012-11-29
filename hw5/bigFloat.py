"""bigfloat: floating-point numbers with very large range"""

# David Chiang
# 2011 July 18

import math
import sys

_LOG_2 = math.log(2.)
_LOG10_2 = math.log10(2.)

_max_exp = sys.float_info.max_exp
_min_exp = sys.float_info.min_exp
assert sys.float_info.radix == 2

def _int_floor(x):
    return int(math.floor(x)) # is this slow?

class BigFloat(object):
    __slots__ = ('sig','exp')
    def __init__(self, sig=0., exp=0):
        # ensure that 0.5 <= s < 1
        s, e = math.frexp(sig)
        self.sig = s
        self.exp = e+exp

    def _align(self, other):
        """Returns a tuple (ss, os, se) such that self == ss * 2**se and other = os * 2**se."""
        ss, se = self.sig, self.exp
        if isinstance(other, BigFloat):
            os, oe = other.sig, other.exp
        else:
            os, oe = other, 0

        if ss == 0.:
            se = oe
        elif os == 0.:
            pass
        elif se > oe:
            os = math.ldexp(os, oe-se)
        elif se < oe:
            ss = math.ldexp(ss, se-oe)
            se = oe
        return ss, os, se

    def __add__(self, other):
        ss, os, e = self._align(other)
        return BigFloat(ss+os, e)
    def __radd__(self, other):
        ss, os, e = self._align(other)
        return BigFloat(os+ss, e)

    def __sub__(self, other):
        ss, os, e = self._align(other)
        return BigFloat(ss-os, e)
    def __rsub__(self, other):
        ss, os, e = self._align(other)
        return BigFloat(os-ss, e)

    def __lt__(self, other):
        ss, os, e = self._align(other)
        return ss < os

    def __pos__(self):
        return BigFloat(self.sig, self.exp)
    def __neg__(self):
        return BigFloat(-self.sig, self.exp)
    def __abs__(self):
        return BigFloat(abs(self.sig), self.exp)

    def __mul__(self, other):
        if isinstance(other, BigFloat):
            return BigFloat(self.sig*other.sig, self.exp+other.exp)
        else:
            return BigFloat(self.sig*other, self.exp)
    def __rmul__(self, other):
        return BigFloat(self.sig*other, self.exp)

    def __div__(self, other):
        if isinstance(other, BigFloat):
            return BigFloat(self.sig/other.sig, self.exp-other.exp)
        else:
            return BigFloat(self.sig/other, self.exp)
    def __rdiv__(self, other):
        return BigFloat(other/self.sig, -self.exp)
    __truediv__ = __div__
    __truerdiv__ = __rdiv__

    def __pow__(self, other):
        # Fast case: other is a small integer
        # We want to compute pow(self.sig, other) and need to make sure
        # that even if self.sig == 0.5, we don't under/overflow
        if isinstance(other, int) and -(_max_exp-1) <= other <= -(_min_exp-1):
            return BigFloat(pow(self.sig, other), self.exp*other)
        else:
            return exp2(other*log2(self))
    def __rpow__(self, other):
        return exp2(float(self)*math.log(other)/_LOG_2)

    def __str__(self):
        if _min_exp <= self.exp <= _max_exp:
            return str(float(self))
        else:
            l = log10(abs(self))
            e10 = _int_floor(l)
            s10 = math.pow(10.,l-e10)
            if 10.-s10 <= 5e-12: # this is the precision of float.__str__
                s10 /= 10.
                e10 += 1
            if self.sig < 0.: s10 = -s10
            return "%se%+d" % (s10,e10)
    def __repr__(self):
        return "bigfloat(%s,%s)" % (self.sig, self.exp)
    def __float__(self):
        return math.ldexp(self.sig, self.exp)

def log2(b):
    return math.log(b.sig)/_LOG_2 + b.exp
def log(b):
    return math.log(b.sig) + b.exp * _LOG_2
def log10(b):
    return math.log10(b.sig) + b.exp * _LOG10_2
def exp2(f):
    e = _int_floor(float(f))
    return BigFloat(math.pow(2.,f-e), e)
def exp(f):
    return exp2(f/_LOG_2)
def exp10(f):
    return exp2(f/_LOG10_2)

if __name__ == "__main__":
    f1 = 1./3.
    f2 = 1./5.
    b1 = BigFloat(f1)
    b2 = BigFloat(f2)

    # add
    print(b1 + 1, f1 + 1)
    print(b1 + 1.5, f1 + 1.5)
    print(b1 + b2, f1 + f2)

    # radd
    print(1 + b1, 1 + f1)
    print(1.5 + b1, 1.5 + f1)

    # mul
    print(b1 * 2, f1 * 2)
    print(b1 * 2.5, f1 * 2.5)
    print(b1 * b2, f1 * f2)

    # rmul
    print(2 * b1, 2 * f1)
    print(2.5 * b1, 2.5 * f1)

    # pow
    print(b1 ** 10, f1 ** 10)
    print(b1 ** 1.1, f1 ** 1.1)
    print(b1 ** -10, f1 ** -10)
    print(b1 ** -1.1, f1 ** -1.1)
    print(10 ** b1, 10 ** f1)
    print(1.1 ** b1, 1.1 ** f1)
    print(-10**b1, -10**f1)
    print(-1.1**b1, -1.1**f1)

    # big numbers
    print(repr(BigFloat(2) ** 1023))
    print(repr(BigFloat(2) ** 1024))
    print(repr(BigFloat(2) ** -1022))
    print(repr(BigFloat(2) ** -1023))
    print(BigFloat(10) ** 308)
    print(BigFloat(10) ** 309)
    print(BigFloat(10) ** -307)
    print(-BigFloat(10)**-307)
    print(BigFloat(10) ** -308)
    print(-BigFloat(10)**-308)
