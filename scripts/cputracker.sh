top -bn11 -d 1 | grep '%Cpu(s)' | sed "s/.*, *\([0-9.]*\)%* id.*/\1/" | tail -10 | awk '{ sum += $1 } END { print 100 - sum / 10 }'
