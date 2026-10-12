"""Run shared 802 regressions plus the synthetic, offline 804 generation transport suite."""
import sys
from validate_aac_v1_2_802 import main
if __name__ == '__main__':
    sys.argv += ['--suite','804']
    main()
