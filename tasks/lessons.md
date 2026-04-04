# Lessons Learned

## Rules
1. Always test on low-memory configurations
2. Use RGB_565 for book covers (half the memory of ARGB_8888)
3. Use DocumentsContract queries, never DocumentFile.listFiles() (10-50x faster)
4. Lazy-init everything not needed for first frame
5. Always persist reading position on every page turn, not just on exit
6. Use SavedStateHandle in ViewModels for process death survival
